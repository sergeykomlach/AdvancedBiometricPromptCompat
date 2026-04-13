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

        private const val TF_OD_API_INPUT_SIZE = 112
        private const val TF_OD_API_IS_QUANTIZED = false
        private const val TF_OD_API_MODEL_FILE = "tf_bio/mobile_face_net.tflite"

        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_END_TIMESTAMP = "lockout_end_timestamp"
        private const val KEY_PERMANENT_LOCKOUT_COUNT = "permanent_lockout_count"
        private const val KEY_ERROR_ACTIVE_UNTIL_TIMESTAMP = "error_active_until_timestamp"

        @Volatile
        private var config: TensorFlowFaceConfig = TensorFlowFaceConfig()

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

    private val effectiveConfig: EffectiveTensorFlowFaceConfig by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TfLiteBackendHelper.resolveEffectiveConfig(config).also {
            LogCat.log(TAG, "Effective config: $it")
        }
    }

    private var frameProvider: IFrameProvider = RealCameraProvider(context)
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val isProcessingFrame = AtomicBoolean(false)
    private val isSessionActive = AtomicBoolean(false)
    private val spoofScoresWindow = ArrayDeque<Float>()
    private var processedFrameCounter = 0
    private var consecutiveMatchCounter = 0
    private var lastMatchedId: String? = null

    private val faceDetector: FaceDetector? by lazy {
        try {
            FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .build()
            )
        } catch (e: Exception) {
            LogCat.logException(e)
            null
        }
    }

    private val recognitionBackend: TfBackendSelection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val ts = System.currentTimeMillis()
        TfLiteBackendHelper.chooseRecognitionBackend(effectiveConfig, "Recognition").also {
            LogCat.log(TAG, "recognitionBackend ${System.currentTimeMillis() - ts}ms; $it")
        }
    }

    private val antiSpoofingBackend: TfBackendSelection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val ts = System.currentTimeMillis()
        TfLiteBackendHelper.chooseAntiSpoofingBackend(effectiveConfig, "AntiSpoofing").also {
            LogCat.log(TAG, "antiSpoofingBackend ${System.currentTimeMillis() - ts}ms; $it")
        }
    }

    private val antiSpoofingRationality: TfBackendRationality by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val ts = System.currentTimeMillis()
        TfLiteBackendHelper.evaluateRationality(
            antiSpoofingBackend,
            "FaceAntiSpoofing",
            effectiveConfig
        ).also {
            LogCat.log(TAG, "antiSpoofingRationality ${System.currentTimeMillis() - ts}ms; $it")
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

    private fun checkLockoutState(): Int? {
        val prefs = getProtectedPreferences(TFLiteObjectDetectionAPIModel.storageName)
        val permanentLockoutCount = prefs.getInt(KEY_PERMANENT_LOCKOUT_COUNT, 0)
        if (permanentLockoutCount >= effectiveConfig.maxTemporaryLockoutsBeforePermanent) {
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
            if (failedAttempts >= effectiveConfig.maxFailedAttemptsBeforeLockout) {
                permanentLockoutCount++
                failedAttempts = 0
                if (permanentLockoutCount < effectiveConfig.maxTemporaryLockoutsBeforePermanent) {
                    putLong(
                        KEY_LOCKOUT_END_TIMESTAMP,
                        System.currentTimeMillis() + effectiveConfig.lockoutDurationMs
                    )
                }
                putInt(KEY_PERMANENT_LOCKOUT_COUNT, permanentLockoutCount)
            }
            putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
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

    private fun setErrorActive(durationMs: Long = effectiveConfig.errorCooldownMs) {
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

        try {
            frameProvider.stop()
        } catch (e: Throwable) {
            LogCat.logException(e)
        }

        isProcessingFrame.set(false)
        processedFrameCounter = 0
        consecutiveMatchCounter = 0
        lastMatchedId = null
        releaseSession(this)
        authCallback = null
        cancellationSignal = null
        isEnrolling = false
        stopBackgroundThread()
    }

    override fun getTimeoutMessage(): CharSequence {
        return LocalizationHelper.getLocalizedString(
            context,
            R.string.biometriccompat_tf_face_help_timeout
        )
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
        processedFrameCounter = 0
        consecutiveMatchCounter = 0
        lastMatchedId = null
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

    private enum class AntiSpoofingStage {
        NONE,
        BEFORE_RECOGNITION,
        AFTER_CANDIDATE
    }

    private sealed class FaceAttemptResult {
        object Success : FaceAttemptResult()
        object MatchInProgress : FaceAttemptResult()
        data class NoMatch(val distance: Float) : FaceAttemptResult()
        object InvalidFace : FaceAttemptResult()
        object Spoof : FaceAttemptResult()
        object FatalError : FaceAttemptResult()
    }

    private fun resolveAntiSpoofingStage(
        frameNumber: Int,
        consecutiveMatches: Int,
        candidateMatched: Boolean
    ): AntiSpoofingStage {
        if (!antiSpoofingEnabled) return AntiSpoofingStage.NONE
        val allowedForFlow = if (isEnrolling) {
            effectiveConfig.antiSpoofingOnEnrollment
        } else {
            effectiveConfig.antiSpoofingOnAuthentication
        }
        if (!allowedForFlow) return AntiSpoofingStage.NONE
        if (frameNumber % effectiveConfig.antiSpoofingFrameStride != 0) {
            return AntiSpoofingStage.NONE
        }

        return when (effectiveConfig.antiSpoofingMode) {
            AntiSpoofingMode.OFF -> AntiSpoofingStage.NONE
            AntiSpoofingMode.BEFORE_RECOGNITION -> AntiSpoofingStage.BEFORE_RECOGNITION
            AntiSpoofingMode.AFTER_CANDIDATE -> if (
                isEnrolling || candidateMatched ||
                consecutiveMatches >= effectiveConfig.antiSpoofingWarmupMatches
            ) {
                AntiSpoofingStage.AFTER_CANDIDATE
            } else {
                AntiSpoofingStage.NONE
            }

            AntiSpoofingMode.AUTO -> if (isEnrolling || candidateMatched) {
                AntiSpoofingStage.AFTER_CANDIDATE
            } else {
                AntiSpoofingStage.NONE
            }
        }
    }

    private fun isSpoofDetected(bitmap: Bitmap): Boolean {
        val engine = antiSpoofing ?: return false
        return try {
            val currentScore = engine.antiSpoofing(bitmap)
            spoofScoresWindow.addLast(currentScore)
            while (spoofScoresWindow.size > effectiveConfig.antiSpoofingWindowSize) {
                spoofScoresWindow.removeFirst()
            }
            if (spoofScoresWindow.size < effectiveConfig.antiSpoofingMinFramesToDecide) {
                return false
            }
            val averageScore = spoofScoresWindow.average().toFloat()
            val highScores =
                spoofScoresWindow.count { it >= effectiveConfig.antiSpoofingScoreThreshold }
            averageScore >= effectiveConfig.antiSpoofingScoreThreshold &&
                    currentScore >= effectiveConfig.antiSpoofingScoreThreshold &&
                    highScores >= effectiveConfig.antiSpoofingMinFramesToDecide
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

    private fun maybeHandleMismatchFailure(distance: Float) {
        val shouldCount = effectiveConfig.countFailedAttemptsForDistantMismatches ||
                distance <= effectiveConfig.maxDistanceThreshold + effectiveConfig.mismatchGraceDistanceDelta
        if (!shouldCount) {
            LogCat.log(TAG, "Mismatch ignored for lockout accounting; distance=$distance")
            return
        }
        handleFailedAttempt()
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
            if (isEnrolling) {
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

                processedFrameCounter++
                when (processSingleFace(bitmap, faces.first())) {
                    FaceAttemptResult.Success,
                    FaceAttemptResult.MatchInProgress,
                    is FaceAttemptResult.NoMatch -> return

                    FaceAttemptResult.InvalidFace -> {
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

                    FaceAttemptResult.Spoof,
                    FaceAttemptResult.FatalError -> return
                }
            }

            val sortedFaces = faces.sortedByDescending {
                it.boundingBox.width() * it.boundingBox.height()
            }

            processedFrameCounter++

            var bestMismatchDistance: Float? = null
            var sawInvalidFace = false

            for (face in sortedFaces) {
                when (val result = processSingleFace(bitmap, face)) {
                    FaceAttemptResult.Success -> return

                    FaceAttemptResult.MatchInProgress -> {
                        return
                    }

                    is FaceAttemptResult.NoMatch -> {
                        bestMismatchDistance = when {
                            bestMismatchDistance == null -> result.distance
                            result.distance < bestMismatchDistance -> result.distance
                            else -> bestMismatchDistance
                        }
                    }

                    FaceAttemptResult.InvalidFace -> {
                        sawInvalidFace = true
                    }

                    FaceAttemptResult.Spoof,
                    FaceAttemptResult.FatalError -> return
                }
            }

            clearAntiSpoofingWindow()
            consecutiveMatchCounter = 0
            lastMatchedId = null

            if (bestMismatchDistance != null) {
                maybeHandleMismatchFailure(bestMismatchDistance)
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
                return
            }

            if (sawInvalidFace) {
                authCallback?.onAuthenticationHelp(
                    CUSTOM_BIOMETRIC_ACQUIRED_PARTIAL,
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_tf_face_help_model_look_straight_ahead
                    )
                )
                return
            }

            clearAntiSpoofingWindow()
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_NO_SPACE,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_tf_face_help_model_not_detected
                )
            )
        } finally {
            LogCat.log(TAG, "processFaces < ${System.currentTimeMillis() - ts}ms")
        }
    }

    private fun processSingleFace(
        bitmap: Bitmap,
        face: Face
    ): FaceAttemptResult {
        if (face.boundingBox.width() < effectiveConfig.minFaceSizePx ||
            face.boundingBox.height() < effectiveConfig.minFaceSizePx
        ) {
            return FaceAttemptResult.InvalidFace
        }

        if (abs(face.headEulerAngleX) > effectiveConfig.maxHeadAngleX ||
            abs(face.headEulerAngleY) > effectiveConfig.maxHeadAngleY
        ) {
            return FaceAttemptResult.InvalidFace
        }

        val livenessCrop = createScaledFaceCrop(
            originalBitmap = bitmap,
            face = face,
            cropScale = effectiveConfig.livenessCropScale,
            outputSize = FaceAntiSpoofing.INPUT_IMAGE_SIZE
        ) ?: return FaceAttemptResult.InvalidFace

        val alignedFace = getAlignedFace(
            originalBitmap = bitmap,
            face = face,
            cropScale = effectiveConfig.recognitionCropScale
        ) ?: run {
            livenessCrop.recycle()
            return FaceAttemptResult.InvalidFace
        }

        try {
            if (!isBitmapBrightEnough(livenessCrop, effectiveConfig.minBrightnessLuma)) {
                return FaceAttemptResult.InvalidFace
            }

            val laplaceScore = if (antiSpoofingEnabled) {
                antiSpoofing?.laplacian(livenessCrop) ?: effectiveConfig.minLaplacianScore
            } else {
                effectiveConfig.minLaplacianScore
            }
            if (laplaceScore < effectiveConfig.minLaplacianScore) {
                return FaceAttemptResult.InvalidFace
            }

            var antiSpoofCheckedThisFace = false
            val antiSpoofStageBefore = resolveAntiSpoofingStage(
                frameNumber = processedFrameCounter,
                consecutiveMatches = consecutiveMatchCounter,
                candidateMatched = false
            )
            if (antiSpoofStageBefore == AntiSpoofingStage.BEFORE_RECOGNITION) {
                antiSpoofCheckedThisFace = true
                if (isSpoofDetected(livenessCrop)) {
                    consecutiveMatchCounter = 0
                    lastMatchedId = null
                    onAuthenticationError(
                        CUSTOM_BIOMETRIC_ERROR_NO_SPACE,
                        LocalizationHelper.getLocalizedString(
                            context,
                            R.string.biometriccompat_tf_face_help_model_fake_face_detected
                        )
                    )
                    return FaceAttemptResult.Spoof
                }
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
                return FaceAttemptResult.FatalError
            }

            val result = results.minByOrNull { it.distance ?: Float.MAX_VALUE }
                ?: return FaceAttemptResult.InvalidFace

            val antiSpoofStageAfter = resolveAntiSpoofingStage(
                frameNumber = processedFrameCounter,
                consecutiveMatches = consecutiveMatchCounter,
                candidateMatched = isEnrolling
            )

            if (isEnrolling) {
                if (!antiSpoofCheckedThisFace &&
                    antiSpoofStageAfter == AntiSpoofingStage.AFTER_CANDIDATE &&
                    isSpoofDetected(livenessCrop)
                ) {
                    clearAntiSpoofingWindow()
                    onAuthenticationError(
                        CUSTOM_BIOMETRIC_ERROR_NO_SPACE,
                        LocalizationHelper.getLocalizedString(
                            context,
                            R.string.biometriccompat_tf_face_help_model_fake_face_detected
                        )
                    )
                    return FaceAttemptResult.Spoof
                }

                result.crop =
                    alignedFace.copy(alignedFace.config ?: Bitmap.Config.ARGB_8888, false)
                detector?.register(enrollmentTag, result)
                LogCat.logError(TAG, "processFaces onAuthenticationSucceeded (enroll)")
                authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                stopAuthentication()
                resetPermanentLockOut()
                return FaceAttemptResult.Success
            }

            val distance = result.distance ?: return FaceAttemptResult.InvalidFace
            val id = result.id
            val matched = distance < effectiveConfig.maxDistanceThreshold

            val antiSpoofStageForMatch = resolveAntiSpoofingStage(
                frameNumber = processedFrameCounter,
                consecutiveMatches = consecutiveMatchCounter,
                candidateMatched = matched
            )
            if (!antiSpoofCheckedThisFace &&
                matched &&
                antiSpoofStageForMatch == AntiSpoofingStage.AFTER_CANDIDATE &&
                isSpoofDetected(livenessCrop)
            ) {
                clearAntiSpoofingWindow()
                consecutiveMatchCounter = 0
                lastMatchedId = null
                onAuthenticationError(
                    CUSTOM_BIOMETRIC_ERROR_NO_SPACE,
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_tf_face_help_model_fake_face_detected
                    )
                )
                return FaceAttemptResult.Spoof
            }

            if (matched) {
                if (id == lastMatchedId) {
                    consecutiveMatchCounter++
                } else {
                    consecutiveMatchCounter = 1
                    lastMatchedId = id
                }

                if (consecutiveMatchCounter >= effectiveConfig.requiredConsecutiveMatches) {
                    LogCat.logError(TAG, "processFaces onAuthenticationSucceeded (auth)")
                    authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                    stopAuthentication()
                    resetPermanentLockOut()
                    return FaceAttemptResult.Success
                }

                return FaceAttemptResult.MatchInProgress
            }

            return FaceAttemptResult.NoMatch(distance)
        } finally {
            if (!alignedFace.isRecycled) alignedFace.recycle()
            if (!livenessCrop.isRecycled) livenessCrop.recycle()
        }
    }

    private fun getAlignedFace(
        originalBitmap: Bitmap,
        face: Face,
        cropScale: Float = effectiveConfig.recognitionCropScale
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
