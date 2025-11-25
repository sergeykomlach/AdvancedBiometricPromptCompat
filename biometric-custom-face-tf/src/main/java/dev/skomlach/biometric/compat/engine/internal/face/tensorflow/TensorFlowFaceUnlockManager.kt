package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import dev.skomlach.biometric.compat.custom.AbstractCustomBiometricManager
import dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider.IFrameProvider
import dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider.RealCameraProvider
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.RuntimeFlavor
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class TensorFlowFaceUnlockManager(
    private val context: Context,
    private var frameProvider: IFrameProvider = RealCameraProvider(context)
) : AbstractCustomBiometricManager() {

    companion object {
        const val IS_ENROLLMENT_KEY = "is_enrollment"
        const val ENROLLMENT_TAG_KEY = "enrollment_tag"

        private const val TIMEOUT_MS = 30000L
        private const val MAX_DISTANCE_THRESHOLD = 0.8f
        private const val REQUIRED_CONSECUTIVE_MATCHES = 3

        private const val TF_OD_API_INPUT_SIZE = 112
        private const val TF_OD_API_IS_QUANTIZED = false
        private const val TF_OD_API_MODEL_FILE = "mobile_face_net.tflite"
        private const val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt"

        private val activeSessionLock = Any()

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var currentActiveManager: TensorFlowFaceUnlockManager? = null


        private fun requestActiveSession(newManager: TensorFlowFaceUnlockManager) {
            synchronized(activeSessionLock) {
                val previous = currentActiveManager
                if (previous != null && previous != newManager) {
                    LogCat.log("FaceAuth", "🛑 Stopping previous active session")
                    previous.cancelInternal()
                }
                currentActiveManager = newManager
            }
        }

        private fun releaseSession(manager: TensorFlowFaceUnlockManager) {
            synchronized(activeSessionLock) {
                if (currentActiveManager == manager) {
                    currentActiveManager = null
                }
            }
        }
    }

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val timeoutHandler = Handler(ExecutorHelper.handler.looper)


    private val isProcessingFrame = AtomicBoolean(false)

    private val isSessionActive = AtomicBoolean(false)

    private var consecutiveMatchCounter = 0
    private var lastMatchedId: String? = null

    // --- MLKit & TFLite ---
    private val faceDetector: FaceDetector? by lazy {
        try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
            FaceDetection.getClient(options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val detector: SimilarityClassifier? by lazy {
        try {
            var interpreterOptions: Interpreter.Options? = Interpreter.Options()
            try {
                interpreterOptions?.addDelegate(GpuDelegateFactory().create(RuntimeFlavor.APPLICATION))
            } catch (e: Exception) {
                try {
                    interpreterOptions?.addDelegate(GpuDelegateFactory().create(RuntimeFlavor.SYSTEM))
                } catch (e2: Exception) {
                }
            }

            TFLiteObjectDetectionAPIModel.create(
                context.assets,
                TF_OD_API_MODEL_FILE,
                TF_OD_API_LABELS_FILE,
                TF_OD_API_INPUT_SIZE,
                TF_OD_API_IS_QUANTIZED,
                interpreterOptions
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private var authCallback: AuthenticationCallback? = null
    private var cancellationSignal: CancellationSignal? = null
    private var isEnrolling: Boolean = false
    private var enrollmentTag: String = "face1"

    fun setFrameProvider(provider: IFrameProvider) {
        this.frameProvider = provider
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("FaceUnlockBackground").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
        }
    }

    private val timeoutRunnable = Runnable {
        if (!isSessionActive.get()) return@Runnable

        LogCat.log("FaceAuth", "Timeout reached")
        authCallback?.onAuthenticationError(
            CUSTOM_BIOMETRIC_ERROR_TIMEOUT,
            "Authentication timed out"
        )
        stopAuthentication()
    }


    private fun cancelInternal() {
        LogCat.log("FaceAuth", "cancelInternal called for $this")
        if (isSessionActive.get()) {
            authCallback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_CANCELED,
                "Cancelled by new operation"
            )
        }
        stopAuthentication()
    }

    private fun stopAuthentication() {

        if (!isSessionActive.compareAndSet(true, false)) {
            return
        }

        LogCat.log("FaceAuth", "stopAuthentication called for $this")


        timeoutHandler.removeCallbacks(timeoutRunnable)


        try {
            frameProvider.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        isProcessingFrame.set(false)
        consecutiveMatchCounter = 0
        lastMatchedId = null


        releaseSession(this)

        authCallback = null
        cancellationSignal = null
        isEnrolling = false

        stopBackgroundThread()
    }

    override fun isHardwareDetected(): Boolean {
        val result = !(detector == null || faceDetector == null)
        LogCat.log("FaceAuth", "isHardwareDetected=$result")
        return result
    }

    override fun hasEnrolledBiometric(): Boolean {
        val result = detector?.hasRegistered() == true
        LogCat.log("FaceAuth", "hasEnrolledBiometric=$result")
        return result
    }

    override fun remove(extra: Bundle?) {

        stopAuthentication()
        detector?.delete(extra?.getString(ENROLLMENT_TAG_KEY))
    }

    override fun authenticate(
        crypto: CryptoObject?,
        flags: Int,
        cancel: CancellationSignal?,
        callback: AuthenticationCallback?,
        handler: Handler?,
        extra: Bundle?
    ) {

        requestActiveSession(this)


        isSessionActive.set(true)
        isEnrolling = extra?.getBoolean(IS_ENROLLMENT_KEY, false) ?: false
        enrollmentTag = extra?.getString(ENROLLMENT_TAG_KEY) ?: "face1"

        LogCat.log("FaceAuth", "authenticate START for $this. Enrolling=$isEnrolling")

        if (!isHardwareDetected()) {
            callback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                "Face detection models not available"
            )
            stopAuthentication()
            return
        }

        if (isEnrolling && enrollmentTag.isEmpty()) {
            callback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                "Enrollment tag not provided"
            )
            stopAuthentication()
            return
        } else if (!isEnrolling && !hasEnrolledBiometric()) {
            callback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC,
                "Biometric not registered"
            )
            stopAuthentication()
            return
        }

        authCallback = callback
        cancellationSignal = cancel


        cancellationSignal?.setOnCancelListener {
            LogCat.log("FaceAuth", "CancellationSignal received")
            if (isSessionActive.get()) {
                authCallback?.onAuthenticationCancelled()
                stopAuthentication()
            }
        }

        startBackgroundThread()

        backgroundHandler?.let { bgHandler ->
            frameProvider.start(
                bgHandler,
                faceDetector!!,
                { bitmap, faces ->

                    if (isSessionActive.get()) {
                        onFrameReceived(bitmap, faces)
                    }
                },
                { code, msg ->
                    if (isSessionActive.get()) {
                        LogCat.log("FaceAuth", "Provider Error: $code, $msg")
                        authCallback?.onAuthenticationError(code, msg)
                        stopAuthentication()
                    }
                }
            )
        }

        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)
    }

    private fun onFrameReceived(fullBitmap: Bitmap, faces: List<Face>) {

        if (!isSessionActive.get()) return

        if (!isProcessingFrame.compareAndSet(false, true)) {
            return
        }

        backgroundHandler?.post {
            try {
                if (isSessionActive.get()) {
                    processFaces(fullBitmap, faces)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessingFrame.set(false)
            }
        }
    }

    private fun processFaces(bitmap: Bitmap, faces: List<Face>) {
        if (faces.isEmpty()) return

        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return

        // Liveness check
        val MAX_ANGLE = 25f
        if (abs(face.headEulerAngleX) > MAX_ANGLE || abs(face.headEulerAngleY) > MAX_ANGLE) {
            authCallback?.onAuthenticationHelp(
                CUSTOM_BIOMETRIC_ACQUIRED_PARTIAL,
                "Look straight ahead"
            )
            return
        }

        // Lighting check
        if (!isBitmapBrightEnough(bitmap, 40)) {
            authCallback?.onAuthenticationHelp(CUSTOM_BIOMETRIC_ACQUIRED_IMAGER_DIRTY, "Too dark")
            return
        }

        val alignedFace = getAlignedFace(bitmap, face) ?: return

        try {
            val results = detector?.recognizeImage(alignedFace, isEnrolling)

            if (!results.isNullOrEmpty()) {
                val result = results[0]

                if (isEnrolling) {
                    if (results.size > 1) {
                        LogCat.log("FaceAuth", "Too many faces on picture")
                        authCallback?.onAuthenticationHelp(
                            CUSTOM_BIOMETRIC_ACQUIRED_INSUFFICIENT,
                            "Retry"
                        )
                        return
                    }
                    LogCat.log("FaceAuth", "Registered: $enrollmentTag")
                    result.crop = alignedFace
                    detector?.register(enrollmentTag, result)
                    authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                    stopAuthentication()
                } else {
                    val distance = result.distance ?: Float.MAX_VALUE
                    val id = result.id
                    val title = result.title

                    LogCat.log("FaceAuth", "Match: $title, Dist: $distance")

                    if (distance < MAX_DISTANCE_THRESHOLD) {
                        if (id == lastMatchedId) {
                            consecutiveMatchCounter++
                        } else {
                            consecutiveMatchCounter = 1
                            lastMatchedId = id
                        }

                        if (consecutiveMatchCounter >= REQUIRED_CONSECUTIVE_MATCHES) {
                            authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                            stopAuthentication()
                        }
                    } else {
                        consecutiveMatchCounter = 0
                        lastMatchedId = null
                        authCallback?.onAuthenticationHelp(
                            CUSTOM_BIOMETRIC_ACQUIRED_INSUFFICIENT,
                            "Retry"
                        )
                    }
                }
            }
        } finally {
            if (alignedFace != bitmap) {
                alignedFace.recycle()
            }
        }
    }

    private fun getAlignedFace(originalBitmap: Bitmap, face: Face): Bitmap? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

        if (leftEye == null || rightEye == null) return null

        val leftEyePos = leftEye.position
        val rightEyePos = rightEye.position

        val deltaX = rightEyePos.x - leftEyePos.x
        val deltaY = rightEyePos.y - leftEyePos.y
        val angle = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()
        val eyeDistance = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()

        val eyeCenter = android.graphics.PointF(
            (leftEyePos.x + rightEyePos.x) / 2f,
            (leftEyePos.y + rightEyePos.y) / 2f
        )


        val desiredEyeDist = TF_OD_API_INPUT_SIZE * 0.38f
        val scale = desiredEyeDist / eyeDistance

        val matrix = Matrix()
        matrix.postTranslate(-eyeCenter.x, -eyeCenter.y)
        matrix.postRotate(-angle)
        matrix.postScale(scale, scale)

        matrix.postTranslate(TF_OD_API_INPUT_SIZE / 2f, TF_OD_API_INPUT_SIZE * 0.45f)

        return Bitmap.createBitmap(
            originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
        ).let {
            val destBitmap = createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE)
            val canvas = android.graphics.Canvas(destBitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(originalBitmap, matrix, paint)
            destBitmap
        }
    }

    private fun isBitmapBrightEnough(bitmap: Bitmap, threshold: Int): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        var totalLum = 0L
        var count = 0

        val startX = width / 4
        val endX = (width * 3) / 4
        val startY = height / 4
        val endY = (height * 3) / 4
        val step = 4

        for (y in startY until endY step step) {
            for (x in startX until endX step step) {
                val px = bitmap[x, y]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                totalLum += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                count++
            }
        }
        return if (count == 0) false else (totalLum / count) >= threshold
    }
}