package dev.skomlach.biometric.compat.engine.internal.face.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder.ImageEmbedderOptions
import dev.skomlach.biometric.compat.custom.AbstractCustomBiometricManager
import dev.skomlach.biometric.compat.engine.internal.face.mediapipe.provider.IFrameProvider
import dev.skomlach.biometric.compat.engine.internal.face.mediapipe.provider.RealCameraProvider
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.storage.SharedPreferenceProvider
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class MediaPipeFaceUnlockManager(
    private val context: Context,
    private var frameProvider: IFrameProvider = RealCameraProvider(context)
) : AbstractCustomBiometricManager() {

    companion object {
        const val IS_ENROLLMENT_KEY = "is_enrollment"
        const val ENROLLMENT_TAG_KEY = "enrollment_tag"
        private const val TIMEOUT_MS = 10000L

        private const val SIMILARITY_THRESHOLD = 0.75f
        private const val REQUIRED_CONSECUTIVE_MATCHES = 3
        private const val MODEL_ASSET = "mobile_face_net.tflite"
        private const val FACE_DETECTION_ASSET = "face_detection_short_range.tflite"
        private const val PREF_NAME = "biometric_mediapipe_storage"

        private val activeSessionLock = Any()

        @Volatile
        private var currentActiveManager: MediaPipeFaceUnlockManager? = null

        private fun requestActiveSession(newManager: MediaPipeFaceUnlockManager) {
            synchronized(activeSessionLock) {
                val previous = currentActiveManager
                if (previous != null && previous != newManager) {
                    previous.cancelInternal()
                }
                currentActiveManager = newManager
            }
        }

        private fun releaseSession(manager: MediaPipeFaceUnlockManager) {
            synchronized(activeSessionLock) {
                if (currentActiveManager == manager) {
                    currentActiveManager = null
                }
            }
        }
    }

    private var faceDetector: FaceDetector? = null
    private var imageEmbedder: ImageEmbedder? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val timeoutHandler = Handler(ExecutorHelper.handler.looper)
    private val isSessionActive = AtomicBoolean(false)
    private val isProcessingFrame = AtomicBoolean(false)

    private var authCallback: AuthenticationCallback? = null
    private var cancellationSignal: CancellationSignal? = null
    private var isEnrolling: Boolean = false
    private var enrollmentTag: String = "face1"

    private var consecutiveMatchCounter = 0
    private var lastMatchedTag: String? = null

    init {
        initMediaPipe()
    }

    private fun initMediaPipe() {
        try {
            val detectorOptions = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder().setModelAssetPath(FACE_DETECTION_ASSET).build()
                )
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(0.5f)
                .build()
            faceDetector = FaceDetector.createFromOptions(context, detectorOptions)

            val embedderOptions = ImageEmbedderOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setRunningMode(RunningMode.IMAGE)
                .setQuantize(false)
                .setL2Normalize(true)
                .build()
            imageEmbedder = ImageEmbedder.createFromOptions(context, embedderOptions)

        } catch (e: Exception) {
            LogCat.logError("MediaPipe", "Init failed: ${e.message}")
        }
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

    override fun isHardwareDetected(): Boolean = faceDetector != null && imageEmbedder != null

    override fun hasEnrolledBiometric(): Boolean {
        val prefs = SharedPreferenceProvider.getPreferences(PREF_NAME)
        return try {
            prefs.all.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    override fun remove(extra: Bundle?) {
        val tag = extra?.getString(ENROLLMENT_TAG_KEY)
        val prefs = SharedPreferenceProvider.getPreferences(PREF_NAME)
        if (tag != null) prefs.edit().remove(tag).apply() else prefs.edit().clear().apply()
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
        authCallback = callback
        cancellationSignal = cancel
        isEnrolling = extra?.getBoolean(IS_ENROLLMENT_KEY, false) ?: false
        enrollmentTag = extra?.getString(ENROLLMENT_TAG_KEY) ?: "face1"

        if (!checkRequirements()) return

        cancellationSignal?.setOnCancelListener {
            if (isSessionActive.get()) {
                authCallback?.onAuthenticationCancelled()
                stopAuthentication()
            }
        }

        startBackgroundThread()

        backgroundHandler?.let { bgHandler ->
            frameProvider.start(
                bgHandler,
                { mpImage ->
                    if (isSessionActive.get()) {
                        onFrameReceived(mpImage)
                    } else {
                        // Якщо сесія не активна, ми все одно мусимо закрити Image!
                        mpImage.close()
                    }
                },
                { code, msg ->
                    if (isSessionActive.get()) {
                        authCallback?.onAuthenticationError(code, msg)
                        stopAuthentication()
                    }
                }
            )
        }

        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)
    }

    private fun checkRequirements(): Boolean {
        if (!isHardwareDetected()) {
            authCallback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                "Models error"
            )
            stopAuthentication()
            return false
        }
        if (!isEnrolling && !hasEnrolledBiometric()) {
            authCallback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC,
                "Not registered"
            )
            stopAuthentication()
            return false
        }
        return true
    }

    private fun onFrameReceived(mpImage: MPImage) {
        // Якщо обробка вже йде, закриваємо кадр і виходимо
        if (!isProcessingFrame.compareAndSet(false, true)) {
            mpImage.close()
            return
        }

        // Запускаємо обробку в фоновому потоці
        backgroundHandler?.post {
            try {
                if (isSessionActive.get()) {
                    processFrame(mpImage)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // ВАЖЛИВО: Завжди закриваємо вхідний MPImage
                // Це звільняє буфер камери для наступного кадру
                mpImage.close()
                isProcessingFrame.set(false)
            }
        }
    }

    private fun processFrame(mpImage: MPImage) {
        // 1. Детекція (Працює прямо на YUV/MPImage - дуже швидко)
        val detectionResult = faceDetector?.detect(mpImage)
        val detections = detectionResult?.detections()

        if (detections.isNullOrEmpty()) return

        val face = detections[0]
        val boundingBox = face.boundingBox() // RectF

        // 2. Якщо обличчя знайдено, нам потрібно зробити кроп для Embedder.
        // ImageEmbedder (стандартний) працює з цілим зображенням, але для кращої точності
        // ми хочемо передати йому тільки обличчя.
        // На жаль, API MediaPipe поки не має "zero-copy crop" для Embedder.
        // Тому ми конвертуємо в Bitmap ТІЛЬКИ ТУТ.
        // Це відбувається набагато рідше, ніж для кожного кадру (тільки коли є обличчя).

        var fullBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null

        try {
            // Конвертуємо MPImage -> Bitmap (використовує GPU/Native, швидко)
            fullBitmap = BitmapExtractor.extract(mpImage)

            // Робимо кроп
            croppedBitmap = cropBitmap(fullBitmap, boundingBox) ?: return

            // Створюємо новий MPImage з кропу
            val croppedMpImage = BitmapImageBuilder(croppedBitmap).build()

            // 3. Ембедінг
            val embedResult = imageEmbedder?.embed(croppedMpImage)?.embeddingResult()
            // У нових версіях .embeddings() доступний прямо з embedResult
            val embedding = embedResult?.embeddings()?.firstOrNull()?.floatEmbedding()

            if (embedding != null) {
                handleEmbeddingResult(embedding)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Очищення проміжних бітмапів
            fullBitmap?.recycle()
            croppedBitmap?.recycle()
        }
    }

    private fun handleEmbeddingResult(currentVector: FloatArray) {
        if (isEnrolling) {
            saveUser(enrollmentTag, currentVector)
            authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
            stopAuthentication()
        } else {
            val (bestTag, similarity) = findNearestUser(currentVector)

            LogCat.log("MediaPipe", "Best match: $bestTag, Similarity: $similarity")

            if (bestTag != null && similarity > SIMILARITY_THRESHOLD) {
                if (bestTag == lastMatchedTag) {
                    consecutiveMatchCounter++
                } else {
                    consecutiveMatchCounter = 1
                    lastMatchedTag = bestTag
                }

                if (consecutiveMatchCounter >= REQUIRED_CONSECUTIVE_MATCHES) {
                    authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                    stopAuthentication()
                }
            } else {
                consecutiveMatchCounter = 0
                lastMatchedTag = null
            }
        }
    }

    // Решта методів (saveUser, findNearestUser, cosineSimilarity, cropBitmap)
    // залишаються такими ж, як у попередньому варіанті.

    private fun saveUser(tag: String, vector: FloatArray) {
        val prefs = SharedPreferenceProvider.getPreferences(PREF_NAME)
        val vectorStr = vector.joinToString(",")
        prefs.edit().putString(tag, vectorStr).apply()
        LogCat.log("MediaPipe", "User registered: $tag")
    }

    private fun findNearestUser(vector: FloatArray): Pair<String?, Float> {
        val prefs = SharedPreferenceProvider.getPreferences(PREF_NAME)
        val allUsers = prefs.all

        var maxSimilarity = -1.0f
        var bestTag: String? = null

        for ((tag, value) in allUsers) {
            if (value is String) {
                try {
                    val storedVector = value.split(",").map { it.toFloat() }.toFloatArray()
                    val sim = cosineSimilarity(vector, storedVector)
                    if (sim > maxSimilarity) {
                        maxSimilarity = sim
                        bestTag = tag
                    }
                } catch (e: Exception) {
                }
            }
        }
        return Pair(bestTag, maxSimilarity)
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        if (v1.size != v2.size) return 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        return if (normA > 0 && normB > 0) dotProduct / (sqrt(normA) * sqrt(normB)) else 0.0f
    }

    private fun cropBitmap(bitmap: Bitmap, rect: RectF): Bitmap? {
        return try {
            val left = rect.left.toInt().coerceAtLeast(0)
            val top = rect.top.toInt().coerceAtLeast(0)
            val availableWidth = bitmap.width - left
            val availableHeight = bitmap.height - top
            val width = rect.width().toInt().coerceAtMost(availableWidth)
            val height = rect.height().toInt().coerceAtMost(availableHeight)
            if (width <= 0 || height <= 0) return null
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            null
        }
    }

    private val timeoutRunnable = Runnable {
        if (isSessionActive.get()) {
            authCallback?.onAuthenticationError(CUSTOM_BIOMETRIC_ERROR_TIMEOUT, "Timeout")
            stopAuthentication()
        }
    }

    private fun cancelInternal() {
        if (isSessionActive.get()) {
            authCallback?.onAuthenticationError(CUSTOM_BIOMETRIC_ERROR_CANCELED, "Canceled")
        }
        stopAuthentication()
    }

    private fun stopAuthentication() {
        if (!isSessionActive.compareAndSet(true, false)) return
        timeoutHandler.removeCallbacks(timeoutRunnable)
        frameProvider.stop()
        releaseSession(this)
        authCallback = null
        cancellationSignal = null
        stopBackgroundThread()
    }
}