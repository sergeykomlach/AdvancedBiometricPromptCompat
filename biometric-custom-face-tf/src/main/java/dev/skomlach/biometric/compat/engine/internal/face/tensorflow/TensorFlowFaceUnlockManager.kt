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
import androidx.core.graphics.scale
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider.IFrameProvider
import dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider.RealCameraProvider
import dev.skomlach.biometric.compat.utils.SensorPrivacyCheck
import dev.skomlach.biometric.custom.face.tf.R
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.storage.SharedPreferenceProvider.getProtectedPreferences
import dev.skomlach.common.translate.LocalizationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class TensorFlowFaceUnlockManager(
    private val context: Context
) : AbstractSoftwareBiometricManager() {

    companion object {
        private const val TAG = "TensorFlowFaceUnlockManager"
        const val IS_ENROLLMENT_KEY = "is_enrollment"
        const val ENROLLMENT_TAG_KEY = "enrollment_tag"

        private const val TIMEOUT_MS = 30000L
        private const val ANTISPOOFING_WINDOW_SIZE = 7
        private const val ANTISPOOFING_MIN_FRAMES_TO_DECIDE = 4
        private const val ANTISPOOFING_SCORE_THRESHOLD = 0.28f

        private const val MAX_ANGLE = 25f
        private const val MIN_FACE_SIZE_PX = 140
        private const val MIN_BRIGHTNESS_LUMA = 45
        private const val RECOGNITION_CROP_SCALE = 1.35f
        private const val LIVENESS_CROP_SCALE = 1.75f
        private const val TF_OD_API_INPUT_SIZE = 112
        private const val TF_OD_API_IS_QUANTIZED = false
        private const val TF_OD_API_MODEL_FILE = "tf_bio/mobile_face_net.tflite"

        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_END_TIMESTAMP = "lockout_end_timestamp"
        private const val KEY_PERMANENT_LOCKOUT_COUNT = "permanent_lockout_count"
        private const val KEY_ERROR_ACTIVE_UNTIL_TIMESTAMP = "error_active_until_timestamp"
        private const val ERROR_ACTIVE_DURATION_MS = 3000L

        private const val MAX_FAILED_ATTEMPTS_BEFORE_LOCKOUT = 5
        private const val MAX_TEMPORARY_LOCKOUTS_BEFORE_PERMANENT = 5
        private const val LOCKOUT_DURATION_MS = 30000L

        @Volatile
        private var config: TensorFlowFaceConfig = TensorFlowFaceConfig()

        private val spoofScoresWindow = ArrayDeque<Float>()
        private val activeSessionLock = Any()

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var currentActiveManager: TensorFlowFaceUnlockManager? = null

        init {
            try {
                val stringIds: Array<Int> = R.string::class.java
                    .fields
                    .asSequence()
                    .filter { it.type == Int::class.javaPrimitiveType }
                    .filter { it.name.startsWith("biometriccompat_") }
                    .mapNotNull { field ->
                        try {
                            field.getInt(null)
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    .toList()
                    .toTypedArray()

                var prefetch: Job? = null
                prefetch = GlobalScope.launch(Dispatchers.IO) {
                    LocalizationHelper.prefetch(AndroidContext.appContext, *stringIds)
                }
                GlobalScope.launch(Dispatchers.Main) {
                    AndroidContext.configurationLiveData.observeForever {
                        prefetch?.cancel()
                        prefetch = GlobalScope.launch(Dispatchers.IO) {
                            LocalizationHelper.prefetch(AndroidContext.appContext, *stringIds)
                        }
                    }
                }
            } catch (e: Throwable) {
                LogCat.logException(e)
            }
        }

        fun setTensorFlowFaceConfig(tensorFlowFaceConfig: TensorFlowFaceConfig) {
            config = tensorFlowFaceConfig
        }

        private fun checkLockoutState(): Int? {
            val prefs = getProtectedPreferences(TFLiteObjectDetectionAPIModel.storageName)
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
                    remove(KEY_FAILED_ATTEMPTS)
                }
            }
            return null
        }

        private fun handleFailedAttempt() {
            val prefs = getProtectedPreferences(TFLiteObjectDetectionAPIModel.storageName)
            var failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            var permanentLockoutCount = prefs.getInt(KEY_PERMANENT_LOCKOUT_COUNT, 0)

            prefs.edit {
                if (failedAttempts >= MAX_FAILED_ATTEMPTS_BEFORE_LOCKOUT) {
                    permanentLockoutCount++
                    failedAttempts = 0
                    if (permanentLockoutCount < MAX_TEMPORARY_LOCKOUTS_BEFORE_PERMANENT) {
                        putLong(KEY_LOCKOUT_END_TIMESTAMP, System.currentTimeMillis() + LOCKOUT_DURATION_MS)
                    }
                    putInt(KEY_PERMANENT_LOCKOUT_COUNT, permanentLockoutCount)
                }
                putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
            }
        }

        private fun requestActiveSession(newManager: TensorFlowFaceUnlockManager) {
            synchronized(activeSessionLock) {
                val previous = currentActiveManager
                if (previous != null && previous != newManager) {
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

    private var frameProvider: IFrameProvider = RealCameraProvider(context)
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val timeoutHandler = Handler(ExecutorHelper.handler.looper)
    private val isProcessingFrame = AtomicBoolean(false)
    private val isSessionActive = AtomicBoolean(false)
    private var consecutiveMatchCounter = 0
    private var lastMatchedId: String? = null

    private val faceDetector: FaceDetector? by lazy {
        try {
            FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()
            )
        } catch (e: Exception) {
            LogCat.logException(e)
            null
        }
    }

    private val recognitionBackend: TfBackendSelection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val ts = System.currentTimeMillis()
        selectRecognitionBackend().also {
            LogCat.log(
                TAG,
                "TfBackendSelection recognitionBackend ${System.currentTimeMillis() - ts}ms; $it"
            )

        }
    }

    private val antiSpoofingBackend: TfBackendSelection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val ts = System.currentTimeMillis()
        selectAntiSpoofingBackend().also {
            LogCat.log(
                TAG,
                "TfBackendSelection antiSpoofingBackend ${System.currentTimeMillis() - ts}ms; $it"
            )

        }
    }

    private val antiSpoofingRationality: TfBackendRationality by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val ts = System.currentTimeMillis()
        TfLiteBackendHelper.evaluateRationality(antiSpoofingBackend, "FaceAntiSpoofing").also {
            LogCat.log(TAG, "TfBackendRationality ${System.currentTimeMillis() - ts}ms; $it")
        }
    }

    private val antiSpoofingEnabled: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        antiSpoofingRationality.antiSpoofingAllowed
    }

    private val antiSpoofing: FaceAntiSpoofing? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (!antiSpoofingEnabled) {
            LogCat.log(TAG, "AntiSpoofingModel disabled: ${antiSpoofingRationality.reason}")
            return@lazy null
        }
        val ts = System.currentTimeMillis()
        try {
            FaceAntiSpoofing.create(context.assets, antiSpoofingBackend).also {
                LogCat.log(TAG, "AntiSpoofingModel init takes ${System.currentTimeMillis() - ts}ms")
            }
        } catch (e: Throwable) {
            LogCat.log(TAG, "AntiSpoofingModel init failed: ${e.message}")
            null
        }
    }

    private val detector: SimilarityClassifier? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            val ts = System.currentTimeMillis()
            TFLiteObjectDetectionAPIModel.create(
                context.assets,
                TF_OD_API_MODEL_FILE,
                TF_OD_API_INPUT_SIZE,
                TF_OD_API_IS_QUANTIZED,
                TfLiteBackendHelper.createOptions(recognitionBackend)
            ).also {
                LogCat.log(
                    TAG,
                    "TFLiteObjectDetectionAPIModel init takes ${System.currentTimeMillis() - ts}ms"
                )
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
            null
        }
    }

    private var authCallback: AuthenticationCallback? = null
    private var cancellationSignal: CancellationSignal? = null
    private var isEnrolling: Boolean = false
    private var enrollmentTag: String = ""

    fun setFrameProvider(provider: IFrameProvider) {
        this.frameProvider = provider
    }

    private fun Handler?.safePost(action: Runnable) {
        this?.let {
            if (it.looper.thread.isAlive) it.post(action)
        }
    }

    private fun isErrorActive(): Boolean {
        val prefs = getProtectedPreferences(TFLiteObjectDetectionAPIModel.storageName)
        val activeUntil = prefs.getLong(KEY_ERROR_ACTIVE_UNTIL_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        if (activeUntil <= now) {
            if (activeUntil != 0L) {
                prefs.edit { remove(KEY_ERROR_ACTIVE_UNTIL_TIMESTAMP) }
            }
            return false
        }
        return true
    }

    private fun setErrorActive(durationMs: Long = ERROR_ACTIVE_DURATION_MS) {
        getProtectedPreferences(TFLiteObjectDetectionAPIModel.storageName).edit {
            putLong(KEY_ERROR_ACTIVE_UNTIL_TIMESTAMP, System.currentTimeMillis() + durationMs)
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
        backgroundHandler = null
        backgroundThread?.quitSafely()
        backgroundThread = null
    }

    private val timeoutRunnable = Runnable {
        if (!isSessionActive.get()) return@Runnable
        onAuthenticationError(
            CUSTOM_BIOMETRIC_ERROR_TIMEOUT,
            LocalizationHelper.getLocalizedString(context, R.string.biometriccompat_tf_face_help_timeout)
        )
        stopAuthentication()
    }

    private fun cancelInternal() {
        if (isSessionActive.get()) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_CANCELED,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_tf_face_help_canceled_by_new_operation
                )
            )
        }
        stopAuthentication()
    }

    private fun stopAuthentication() {
        if (!isSessionActive.compareAndSet(true, false)) return

        spoofScoresWindow.clear()
        timeoutHandler.removeCallbacks(timeoutRunnable)
        try {
            frameProvider.stop()
        } catch (e: Throwable) {
            LogCat.logException(e)
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

    override fun resetLockOut() {
        getProtectedPreferences(TFLiteObjectDetectionAPIModel.storageName).edit {
            remove(KEY_LOCKOUT_END_TIMESTAMP)
            remove(KEY_FAILED_ATTEMPTS)
        }
    }

    override fun resetPermanentLockOut() {
        getProtectedPreferences(TFLiteObjectDetectionAPIModel.storageName).edit {
            remove(KEY_FAILED_ATTEMPTS)
            remove(KEY_LOCKOUT_END_TIMESTAMP)
            remove(KEY_PERMANENT_LOCKOUT_COUNT)
        }
    }

    override fun getPermissions(): List<String> = listOf(Manifest.permission.CAMERA)
    override val biometricType: BiometricType = BiometricType.BIOMETRIC_FACE

    override fun isHardwareDetected(): Boolean {
        return detector != null && faceDetector != null && frameProvider.isHardwareSupported()
    }

    override fun hasEnrolledBiometric(): Boolean = detector?.hasRegistered() == true
    override fun getManagers(): Set<Any> = emptySet()

    override fun remove(extra: Bundle?) {
        stopAuthentication()
        detector?.delete(extra?.getString(ENROLLMENT_TAG_KEY))
    }

    override fun getEnrollBundle(name: String?): Bundle {
        return Bundle().apply {
            putBoolean(IS_ENROLLMENT_KEY, true)
            putString(ENROLLMENT_TAG_KEY, name ?: "face${(detector?.registeredCount() ?: 0) + 1}")
        }
    }

    override fun getEnrolls(): Collection<String> = detector?.getEnrolls() ?: emptyList()

    override fun authenticate(
        crypto: CryptoObject?,
        flags: Int,
        cancel: CancellationSignal?,
        callback: AuthenticationCallback?,
        handler: Handler?,
        extra: Bundle?
    ) {
        requestActiveSession(this)
        val lockoutError = checkLockoutState()
        if (lockoutError != null) {
            val msg = if (lockoutError == CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT) {
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_tf_face_help_too_many_attempts_permanent
                )
            } else {
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_tf_face_help_too_many_attempts_try_later
                )
            }
            onAuthenticationError(lockoutError, msg)
            releaseSession(this)
            return
        }

        isSessionActive.set(true)
        spoofScoresWindow.clear()
        isEnrolling = extra?.getBoolean(IS_ENROLLMENT_KEY, false) ?: false
        enrollmentTag = extra?.getString(ENROLLMENT_TAG_KEY)
            ?: "face${(detector?.registeredCount() ?: 0) + 1}"

        if (!isHardwareDetected()) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_tf_face_help_model_not_available
                )
            )
            stopAuthentication()
            return
        }
        if (frameProvider is RealCameraProvider && SensorPrivacyCheck.isCameraBlocked()) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_tf_face_help_model_camera_disabled
                )
            )
            stopAuthentication()
            return
        }
        if (frameProvider is RealCameraProvider && SensorPrivacyCheck.isCameraInUse()) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_LOCKOUT,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_tf_face_help_model_camera_locked_out
                )
            )
            stopAuthentication()
            return
        }
        if (isEnrolling && enrollmentTag.isEmpty()) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_tf_face_help_model_enrollment_tag_not_provided
                )
            )
            stopAuthentication()
            return
        } else if (!isEnrolling && !hasEnrolledBiometric()) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_tf_face_help_model_not_registered
                )
            )
            stopAuthentication()
            return
        }

        authCallback = callback
        cancellationSignal = cancel
        cancellationSignal?.setOnCancelListener {
            if (isSessionActive.get()) {
                authCallback?.onAuthenticationCancelled()
                stopAuthentication()
            }
        }

        startBackgroundThread()
        frameProvider.start(
            faceDetector!!,
            { bitmap, faces -> if (isSessionActive.get()) onFrameReceived(bitmap, faces) },
            { code, msg ->
                if (isSessionActive.get()) {
                    onAuthenticationError(code, msg)
                    stopAuthentication()
                }
            }
        )
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)
    }

    private fun onFrameReceived(fullBitmap: Bitmap, faces: List<Face>) {
        if (!isSessionActive.get()) return
        if (!isProcessingFrame.compareAndSet(false, true)) return
        backgroundHandler.safePost {
            try {
                if (isSessionActive.get()) processFaces(fullBitmap, faces)
            } catch (e: Throwable) {
                LogCat.logException(e)
            } finally {
                isProcessingFrame.set(false)
            }
        }
    }

    private fun clearAntiSpoofingWindow() {
        spoofScoresWindow.clear()
    }

    private fun analyzeAntiSpoofing(bitmap: Bitmap): Boolean {
        val engine = antiSpoofing ?: return false
        return try {
            val currentScore = engine.antiSpoofing(bitmap)
            spoofScoresWindow.addLast(currentScore)
            while (spoofScoresWindow.size > ANTISPOOFING_WINDOW_SIZE) {
                spoofScoresWindow.removeFirst()
            }
            if (spoofScoresWindow.size < ANTISPOOFING_MIN_FRAMES_TO_DECIDE) {
                return false
            }
            val averageScore = spoofScoresWindow.average().toFloat()
            val highScores = spoofScoresWindow.count { it >= ANTISPOOFING_SCORE_THRESHOLD }
            averageScore >= ANTISPOOFING_SCORE_THRESHOLD &&
                    currentScore >= ANTISPOOFING_SCORE_THRESHOLD &&
                    highScores >= ANTISPOOFING_MIN_FRAMES_TO_DECIDE
        } catch (e: Throwable) {
            LogCat.logException(e, TAG)
            false
        }
    }

    private fun onAuthenticationError(code: Int, msg: String) {
        if (isErrorActive()) return
        setErrorActive()
        authCallback?.onAuthenticationError(code, msg)
    }

    private fun processFaces(bitmap: Bitmap, faces: List<Face>) {
        val ts = System.currentTimeMillis()
        LogCat.log(TAG, "processFaces >")
        try {
            if (isErrorActive()) return
            if (faces.isEmpty()) {
                clearAntiSpoofingWindow()
                onAuthenticationError(
                    CUSTOM_BIOMETRIC_ERROR_NO_SPACE,
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_tf_face_help_model_not_detected
                    )
                )
                return
            }
            if (faces.size > 1) {
                clearAntiSpoofingWindow()
                onAuthenticationError(
                    CUSTOM_BIOMETRIC_ERROR_NO_SPACE,
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_tf_face_help_model_too_many_faces
                    )
                )
                return
            }

            val face =
                faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return
            if (face.boundingBox.width() < MIN_FACE_SIZE_PX || face.boundingBox.height() < MIN_FACE_SIZE_PX) {
                clearAntiSpoofingWindow()
                authCallback?.onAuthenticationHelp(
                    CUSTOM_BIOMETRIC_ACQUIRED_TOO_FAST,
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_tf_face_help_model_look_straight_ahead
                    )
                )
                return
            }
            if (abs(face.headEulerAngleX) > MAX_ANGLE || abs(face.headEulerAngleY) > MAX_ANGLE) {
                clearAntiSpoofingWindow()
                authCallback?.onAuthenticationHelp(
                    CUSTOM_BIOMETRIC_ACQUIRED_PARTIAL,
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_tf_face_help_model_look_straight_ahead
                    )
                )
                return
            }

            val livenessCrop = createScaledFaceCrop(
                originalBitmap = bitmap,
                face = face,
                cropScale = LIVENESS_CROP_SCALE,
                outputSize = FaceAntiSpoofing.INPUT_IMAGE_SIZE
            ) ?: run {
                clearAntiSpoofingWindow()
                return
            }

            val alignedFace = getAlignedFace(
                originalBitmap = bitmap,
                face = face,
                cropScale = RECOGNITION_CROP_SCALE
            ) ?: run {
                livenessCrop.recycle()
                clearAntiSpoofingWindow()
                return
            }

            try {
                if (!isBitmapBrightEnough(livenessCrop, MIN_BRIGHTNESS_LUMA)) {
                    clearAntiSpoofingWindow()
                    authCallback?.onAuthenticationHelp(
                        CUSTOM_BIOMETRIC_ACQUIRED_IMAGER_DIRTY,
                        LocalizationHelper.getLocalizedString(
                            context,
                            R.string.biometriccompat_tf_face_help_model_too_dark
                        )
                    )
                    return
                }

                val laplaceScore = if (antiSpoofingEnabled) {
                    antiSpoofing?.laplacian(livenessCrop) ?: FaceAntiSpoofing.LAPLACIAN_THRESHOLD
                } else {
                    FaceAntiSpoofing.LAPLACIAN_THRESHOLD
                }
                if (laplaceScore < FaceAntiSpoofing.LAPLACIAN_THRESHOLD) {
                    clearAntiSpoofingWindow()
                    authCallback?.onAuthenticationHelp(
                        CUSTOM_BIOMETRIC_ACQUIRED_INSUFFICIENT,
                        LocalizationHelper.getLocalizedString(
                            context,
                            R.string.biometriccompat_tf_face_help_model_image_is_blurry
                        )
                    )
                    return
                }

                if (antiSpoofingEnabled && analyzeAntiSpoofing(livenessCrop)) {
                    consecutiveMatchCounter = 0
                    lastMatchedId = null
                    onAuthenticationError(
                        CUSTOM_BIOMETRIC_ERROR_NO_SPACE,
                        LocalizationHelper.getLocalizedString(
                            context,
                            R.string.biometriccompat_tf_face_help_model_fake_face_detected
                        )
                    )
                    return
                }

                val results = detector?.recognizeImage(alignedFace, isEnrolling)
                if (results.isNullOrEmpty()) {
                    clearAntiSpoofingWindow()
                    onAuthenticationError(
                        CUSTOM_BIOMETRIC_ERROR_NO_SPACE,
                        LocalizationHelper.getLocalizedString(
                            context,
                            R.string.biometriccompat_tf_face_help_model_not_detected
                        )
                    )
                    return
                }

                val result = results.first()
                if (isEnrolling) {
                    result.crop =
                        alignedFace.copy(alignedFace.config ?: Bitmap.Config.ARGB_8888, false)
                    detector?.register(enrollmentTag, result)
                    LogCat.logError(TAG, "processFaces onAuthenticationSucceeded (enroll)")
                    authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                    stopAuthentication()
                    resetPermanentLockOut()
                    return
                }

                val distance = result.distance ?: return
                val id = result.id
                if (distance < config.maxDistanceThresholds) {
                    if (id == lastMatchedId) {
                        consecutiveMatchCounter++
                    } else {
                        consecutiveMatchCounter = 1
                        lastMatchedId = id
                    }
                    if (consecutiveMatchCounter >= config.requiredConsecutiveMatches) {
                        LogCat.logError(TAG, "processFaces onAuthenticationSucceeded (auth)")
                        authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                        stopAuthentication()
                        resetPermanentLockOut()
                    }
                } else {
                    clearAntiSpoofingWindow()
                    consecutiveMatchCounter = 0
                    lastMatchedId = null
                    handleFailedAttempt()
                    val lockoutError = checkLockoutState()
                    if (lockoutError != null) {
                        val msg = if (lockoutError == CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT) {
                            LocalizationHelper.getLocalizedString(
                                context,
                                R.string.biometriccompat_tf_face_help_too_many_attempts_permanent
                            )
                        } else {
                            LocalizationHelper.getLocalizedString(
                                context,
                                R.string.biometriccompat_tf_face_help_too_many_attempts_try_later
                            )
                        }
                        onAuthenticationError(lockoutError, msg)
                        stopAuthentication()
                    } else {
                        onAuthenticationError(
                            CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                            LocalizationHelper.getLocalizedString(
                                context,
                                R.string.biometriccompat_tf_face_help_model_retry
                            )
                        )
                    }
                }
            } finally {
                if (!alignedFace.isRecycled) alignedFace.recycle()
                if (!livenessCrop.isRecycled) livenessCrop.recycle()
            }
        } finally {
            LogCat.log(TAG, "processFaces < ${System.currentTimeMillis() - ts}ms")
        }
    }

    private fun selectRecognitionBackend(): TfBackendSelection {
        return TfLiteBackendHelper.chooseRecognitionBackendHeuristic("Recognition")
    }

    private fun selectAntiSpoofingBackend(): TfBackendSelection {
        return TfLiteBackendHelper.chooseAntiSpoofingBackendHeuristic("AntiSpoofing")
    }

    private fun getAlignedFace(
        originalBitmap: Bitmap,
        face: Face,
        cropScale: Float = RECOGNITION_CROP_SCALE
    ): Bitmap? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
        if (leftEye == null || rightEye == null) return null

        val cropRect = buildExpandedFaceRect(originalBitmap, face, cropScale)
        val sourceBitmap = Bitmap.createBitmap(
            originalBitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        )

        return try {
            val leftEyePos = leftEye.position
            val rightEyePos = rightEye.position
            val deltaX = rightEyePos.x - leftEyePos.x
            val deltaY = rightEyePos.y - leftEyePos.y
            val angle = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()
            val eyeDistance = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
            if (eyeDistance <= 0f) return null

            val eyeCenter = android.graphics.PointF(
                ((leftEyePos.x + rightEyePos.x) / 2f) - cropRect.left,
                ((leftEyePos.y + rightEyePos.y) / 2f) - cropRect.top
            )

            val desiredEyeDist = TF_OD_API_INPUT_SIZE * 0.38f
            val scale = desiredEyeDist / eyeDistance

            val matrix = Matrix().apply {
                postTranslate(-eyeCenter.x, -eyeCenter.y)
                postRotate(-angle)
                postScale(scale, scale)
                postTranslate(TF_OD_API_INPUT_SIZE / 2f, TF_OD_API_INPUT_SIZE * 0.45f)
            }

            createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE).also { destBitmap ->
                val canvas = android.graphics.Canvas(destBitmap)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    isFilterBitmap = true
                    isDither = true
                }
                canvas.drawBitmap(sourceBitmap, matrix, paint)
            }
        } finally {
            sourceBitmap.recycle()
        }
    }

    private fun createScaledFaceCrop(
        originalBitmap: Bitmap,
        face: Face,
        cropScale: Float,
        outputSize: Int
    ): Bitmap? {
        val cropRect = buildExpandedFaceRect(originalBitmap, face, cropScale)
        if (cropRect.width() <= 1 || cropRect.height() <= 1) return null
        val croppedBitmap = Bitmap.createBitmap(
            originalBitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        )
        return try {
            croppedBitmap.scale(outputSize, outputSize)
        } finally {
            croppedBitmap.recycle()
        }
    }

    private fun buildExpandedFaceRect(
        originalBitmap: Bitmap,
        face: Face,
        cropScale: Float
    ): android.graphics.Rect {
        val boundingBox = face.boundingBox
        val centerX = boundingBox.exactCenterX()
        val centerY = boundingBox.exactCenterY()
        val cropWidth = (boundingBox.width() * cropScale).toInt().coerceAtLeast(2)
        val cropHeight = (boundingBox.height() * cropScale).toInt().coerceAtLeast(2)
        val left = (centerX - cropWidth / 2f).toInt().coerceIn(0, originalBitmap.width - 2)
        val top = (centerY - cropHeight / 2f).toInt().coerceIn(0, originalBitmap.height - 2)
        val right = (left + cropWidth).coerceIn(left + 1, originalBitmap.width)
        val bottom = (top + cropHeight).coerceIn(top + 1, originalBitmap.height)
        return android.graphics.Rect(left, top, right, bottom)
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
        return count != 0 && (totalLum / count) >= threshold
    }
}
