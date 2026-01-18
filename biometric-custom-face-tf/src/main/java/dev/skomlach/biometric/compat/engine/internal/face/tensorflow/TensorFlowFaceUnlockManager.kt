package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.AbstractCustomBiometricManager
import dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider.IFrameProvider
import dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider.RealCameraProvider
import dev.skomlach.biometric.compat.utils.SensorPrivacyCheck
import dev.skomlach.biometric.custom.face.tf.R
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.storage.SharedPreferenceProvider.getCryptoPreferences
import dev.skomlach.common.translate.LocalizationHelper
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.RuntimeFlavor
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class TensorFlowFaceUnlockManager(
    private val context: Context
) : AbstractCustomBiometricManager() {

    companion object {
        private const val TAG = "TensorFlowFaceUnlockManager"
        const val IS_ENROLLMENT_KEY = "is_enrollment"
        const val ENROLLMENT_TAG_KEY = "enrollment_tag"

        private const val TIMEOUT_MS = 30000L


        private const val MAX_ANGLE = 25f
        private const val TF_OD_API_INPUT_SIZE = 112
        private const val TF_OD_API_IS_QUANTIZED = false
        private const val TF_OD_API_MODEL_FILE = "tf_bio/mobile_face_net.tflite"

        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_END_TIMESTAMP = "lockout_end_timestamp"
        private const val KEY_PERMANENT_LOCKOUT_COUNT = "permanent_lockout_count"

        private const val MAX_FAILED_ATTEMPTS_BEFORE_LOCKOUT = 5
        private const val MAX_TEMPORARY_LOCKOUTS_BEFORE_PERMANENT = 5
        private const val LOCKOUT_DURATION_MS = 30000L // 30 sec
        private var config: TensorFlowFaceConfig = TensorFlowFaceConfig()
        fun setTensorFlowFaceConfig(tensorFlowFaceConfig: TensorFlowFaceConfig) {
            config = tensorFlowFaceConfig
        }

        fun resetLockoutCounters() {
            getCryptoPreferences(TFLiteObjectDetectionAPIModel.storageName).edit {
                remove(KEY_FAILED_ATTEMPTS)
                    .remove(KEY_LOCKOUT_END_TIMESTAMP)
                    .remove(KEY_PERMANENT_LOCKOUT_COUNT)
            }
        }

        private fun checkLockoutState(): Int? {
            val prefs = getCryptoPreferences(TFLiteObjectDetectionAPIModel.storageName)
            val permanentLockoutCount = prefs.getInt(KEY_PERMANENT_LOCKOUT_COUNT, 0)

            if (permanentLockoutCount >= MAX_TEMPORARY_LOCKOUTS_BEFORE_PERMANENT) {
                return CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
            }

            val lockoutEndTime = prefs.getLong(KEY_LOCKOUT_END_TIMESTAMP, 0)
            val currentTime = System.currentTimeMillis()

            if (lockoutEndTime > currentTime) {
                return CUSTOM_BIOMETRIC_ERROR_LOCKOUT
            } else if (lockoutEndTime > 0) {
                prefs.edit {
                    remove(KEY_LOCKOUT_END_TIMESTAMP)
                        .remove(KEY_FAILED_ATTEMPTS)
                }
            }

            return null
        }

        private fun handleFailedAttempt() {
            val prefs = getCryptoPreferences(TFLiteObjectDetectionAPIModel.storageName)
            var failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            var permanentLockoutCount = prefs.getInt(KEY_PERMANENT_LOCKOUT_COUNT, 0)

            prefs.edit {

                if (failedAttempts >= MAX_FAILED_ATTEMPTS_BEFORE_LOCKOUT) {

                    permanentLockoutCount++
                    failedAttempts = 0

                    if (permanentLockoutCount >= MAX_TEMPORARY_LOCKOUTS_BEFORE_PERMANENT) {
                        LogCat.log(TAG, "Permanent Lockout Activated")
                    } else {
                        val lockoutEnd = System.currentTimeMillis() + LOCKOUT_DURATION_MS
                        putLong(KEY_LOCKOUT_END_TIMESTAMP, lockoutEnd)
                        LogCat.log(TAG, "Temporary Lockout Activated for 30s")
                    }

                    putInt(KEY_PERMANENT_LOCKOUT_COUNT, permanentLockoutCount)
                }

                putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
            }
        }

        private val activeSessionLock = Any()

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var currentActiveManager: TensorFlowFaceUnlockManager? = null


        private fun requestActiveSession(newManager: TensorFlowFaceUnlockManager) {
            synchronized(activeSessionLock) {
                val previous = currentActiveManager
                if (previous != null && previous != newManager) {
                    LogCat.log(TAG, "🛑 Stopping previous active session")
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

        init {
            DeviceUnlockedReceiver.registerListener()
        }
    }

    private var frameProvider: IFrameProvider = RealCameraProvider(context)
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
    private val antiSpoofing: FaceAntiSpoofing? by lazy {
        try {
            FaceAntiSpoofing(context.assets)
        } catch (e: Exception) {
            LogCat.log(TAG, "AntiSpoofing model init failed: ${e.message}")
            null
        }
    }
    private val detector: SimilarityClassifier? by lazy {
        try {
            var interpreterOptions: Interpreter.Options? = Interpreter.Options()
            try {
                interpreterOptions?.addDelegate(GpuDelegateFactory().create(RuntimeFlavor.SYSTEM))
            } catch (e: Exception) {
                try {
                    interpreterOptions?.addDelegate(GpuDelegateFactory().create(RuntimeFlavor.APPLICATION))
                } catch (e2: Exception) {
                }
            }

            TFLiteObjectDetectionAPIModel.create(
                context.assets,
                TF_OD_API_MODEL_FILE,
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

        LogCat.log(TAG, "Timeout reached")
        authCallback?.onAuthenticationError(
            CUSTOM_BIOMETRIC_ERROR_TIMEOUT,
            LocalizationHelper.getLocalizedString(context, R.string.tf_face_help_timeout)
        )
        stopAuthentication()
    }


    private fun cancelInternal() {
        LogCat.log(TAG, "cancelInternal called for $this")
        if (isSessionActive.get()) {
            authCallback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_CANCELED,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.tf_face_help_canceled_by_new_operation
                )
            )
        }
        stopAuthentication()
    }

    private fun stopAuthentication() {

        if (!isSessionActive.compareAndSet(true, false)) {
            return
        }

        LogCat.log(TAG, "stopAuthentication called for $this")


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

    override fun getPermissions(): List<String> {
        LogCat.log(TAG, "getPermissions")
        return listOf(Manifest.permission.CAMERA)
    }

    override val biometricType: BiometricType = BiometricType.BIOMETRIC_FACE
    override fun isHardwareDetected(): Boolean {
        val result = !(detector == null || faceDetector == null)
        LogCat.log(TAG, "isHardwareDetected=$result")
        return result
    }

    override fun hasEnrolledBiometric(): Boolean {
        val result = detector?.hasRegistered() == true
        LogCat.log(TAG, "hasEnrolledBiometric=$result")
        return result
    }

    override fun getManagers(): Set<Any> {
        LogCat.log(TAG, "getManagers")
        return emptySet()
    }


    override fun remove(extra: Bundle?) {
        LogCat.log(TAG, "remove")
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
        LogCat.log(TAG, "authenticate")
        requestActiveSession(this)
        val lockoutError = checkLockoutState()
        if (lockoutError != null) {
            val msg = if (lockoutError == CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT)
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.tf_face_help_too_many_attempts_permanent
                )
            else
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.tf_face_help_too_many_attempts_try_later
                )

            callback?.onAuthenticationError(lockoutError, msg)

            releaseSession(this)
            return
        }

        isSessionActive.set(true)
        isEnrolling = extra?.getBoolean(IS_ENROLLMENT_KEY, false) ?: false
        enrollmentTag = extra?.getString(ENROLLMENT_TAG_KEY) ?: "face1"

        LogCat.log(TAG, "authenticate START for $this. Enrolling=$isEnrolling")

        if (!isHardwareDetected()) {
            callback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.tf_face_help_model_not_available
                )
            )
            stopAuthentication()
            return
        }
        if (SensorPrivacyCheck.isCameraBlocked()) {
            callback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.tf_face_help_model_camera_disabled
                )
            )
            stopAuthentication()
            return
        }
        if (SensorPrivacyCheck.isCameraInUse()) {
            callback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_LOCKOUT,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.tf_face_help_model_camera_locked_out
                )
            )
            stopAuthentication()
            return
        }
        if (isEnrolling && enrollmentTag.isEmpty()) {
            callback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.tf_face_help_model_enrollment_tag_not_provided
                )
            )
            stopAuthentication()
            return
        } else if (!isEnrolling && !hasEnrolledBiometric()) {
            callback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.tf_face_help_model_not_registered
                )
            )
            stopAuthentication()
            return
        }

        authCallback = callback
        cancellationSignal = cancel


        cancellationSignal?.setOnCancelListener {
            LogCat.log(TAG, "CancellationSignal received")
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
                        LogCat.log(TAG, "Provider Error: $code, $msg")
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

        if (abs(face.headEulerAngleX) > MAX_ANGLE || abs(face.headEulerAngleY) > MAX_ANGLE) {
            authCallback?.onAuthenticationHelp(
                CUSTOM_BIOMETRIC_ACQUIRED_PARTIAL,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.tf_face_help_model_look_straight_ahead
                )
            )
            return
        }

        // Lighting check
        if (!isBitmapBrightEnough(bitmap, 40)) {
            authCallback?.onAuthenticationHelp(
                CUSTOM_BIOMETRIC_ACQUIRED_IMAGER_DIRTY,
                LocalizationHelper.getLocalizedString(context, R.string.tf_face_help_model_too_dark)
            )
            return
        }
        val laplaceScore =
            antiSpoofing?.laplacian(bitmap) ?: FaceAntiSpoofing.LAPLACIAN_THRESHOLD

        if (laplaceScore < FaceAntiSpoofing.LAPLACIAN_THRESHOLD) {
            LogCat.log(TAG, "Image too blurry: $laplaceScore")
            authCallback?.onAuthenticationHelp(
                CUSTOM_BIOMETRIC_ACQUIRED_INSUFFICIENT,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.tf_face_help_model_image_is_blurry
                )
            )
            return
        }
        if (!isEnrolling) {
            val spoofScore =
                antiSpoofing?.antiSpoofing(bitmap) ?: FaceAntiSpoofing.THRESHOLD
            LogCat.log(TAG, "Spoof Score: $spoofScore")

            if (spoofScore > FaceAntiSpoofing.THRESHOLD) {
                LogCat.log(TAG, "Spoof attack detected! Score: $spoofScore")
                handleFailedAttempt()

                val lockoutError = checkLockoutState()
                if (lockoutError != null) {
                    val msg = if (lockoutError == CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT)
                        LocalizationHelper.getLocalizedString(
                            context,
                            R.string.tf_face_help_too_many_attempts_permanent
                        )
                    else
                        LocalizationHelper.getLocalizedString(
                            context,
                            R.string.tf_face_help_too_many_attempts_try_later
                        )

                    authCallback?.onAuthenticationError(lockoutError, msg)
                    stopAuthentication()
                    return
                }
                authCallback?.onAuthenticationHelp(
                    CUSTOM_BIOMETRIC_ACQUIRED_INSUFFICIENT,
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.tf_face_help_model_fake_face_detected
                    )
                )
                return
            }
        }
        val alignedFace = getAlignedFace(bitmap, face) ?: return

        try {
            val results = detector?.recognizeImage(alignedFace, isEnrolling)

            if (!results.isNullOrEmpty()) {
                val result = results[0]

                if (isEnrolling) {
                    if (results.size > 1) {
                        LogCat.log(TAG, "Too many faces on picture")
                        authCallback?.onAuthenticationHelp(
                            CUSTOM_BIOMETRIC_ACQUIRED_INSUFFICIENT,
                            LocalizationHelper.getLocalizedString(
                                context,
                                R.string.tf_face_help_model_retry
                            )
                        )
                        return
                    }
                    LogCat.log(TAG, "Registered: $enrollmentTag")
                    result.crop = alignedFace
                    detector?.register(enrollmentTag, result)
                    authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                    stopAuthentication()
                } else {
                    val distance = result.distance ?: Float.MAX_VALUE
                    val id = result.id
                    val title = result.title

                    LogCat.log(TAG, "Match: $title, Dist: $distance")

                    if (distance < config.maxDistanceThresholds) {
                        if (id == lastMatchedId) {
                            consecutiveMatchCounter++
                        } else {
                            consecutiveMatchCounter = 1
                            lastMatchedId = id
                        }

                        if (consecutiveMatchCounter >= config.requiredConsecutiveMatches) {
                            resetLockoutCounters()
                            authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                            stopAuthentication()
                        }
                    } else {
                        consecutiveMatchCounter = 0
                        lastMatchedId = null
                        handleFailedAttempt()

                        val lockoutError = checkLockoutState()
                        if (lockoutError != null) {
                            val msg = if (lockoutError == CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT)
                                LocalizationHelper.getLocalizedString(
                                    context,
                                    R.string.tf_face_help_too_many_attempts_permanent
                                )
                            else
                                LocalizationHelper.getLocalizedString(
                                    context,
                                    R.string.tf_face_help_too_many_attempts_try_later
                                )

                            authCallback?.onAuthenticationError(lockoutError, msg)
                            stopAuthentication()
                            return
                        }
                        authCallback?.onAuthenticationHelp(
                            CUSTOM_BIOMETRIC_ACQUIRED_INSUFFICIENT,
                            LocalizationHelper.getLocalizedString(
                                context,
                                R.string.tf_face_help_model_retry
                            )
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