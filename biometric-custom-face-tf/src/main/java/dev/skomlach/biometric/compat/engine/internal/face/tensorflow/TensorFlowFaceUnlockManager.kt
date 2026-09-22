package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import dev.skomlach.biometric.compat.custom.SoftwareBiometricEnrollment
import dev.skomlach.biometric.compat.custom.SoftwareBiometricAssuranceLevel
import dev.skomlach.biometric.compat.custom.SoftwareBiometricSecurityProfile
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkSession
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkerCallback
import dev.skomlach.biometric.compat.custom.newSoftwareBiometricWorker
import dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider.CaptureContinuityProvider
import dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider.IFrameProvider
import dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider.RealCameraProvider
import dev.skomlach.biometric.compat.utils.SensorPrivacyCheck
import dev.skomlach.biometric.custom.face.tf.R
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.storage.ProtectedStorageUnavailableException
import dev.skomlach.common.storage.SharedPreferenceProvider.getProtectedPreferences
import dev.skomlach.common.translate.LocalizationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

class TensorFlowFaceUnlockManager(
    private val context: Context
) : AbstractSoftwareBiometricManager() {

    override val priority: Int = PRIORITY_BELOW_SYSTEM_HARDWARE
    override val securityProfile: SoftwareBiometricSecurityProfile
        get() = SoftwareBiometricSecurityProfile(
            biometricType = BiometricType.BIOMETRIC_FACE,
            assurance = if (effectiveConfig.base.faceChallengeEnabled) {
                SoftwareBiometricAssuranceLevel.ACTIVE_CHALLENGE
            } else {
                SoftwareBiometricAssuranceLevel.PASSIVE_MATCH
            },
            requiresTrustedCapture = true,
            allowsCompatibilityCapture = false,
            supportsCryptoObject = false,
            maxCaptureDurationMs = 30_000L
        )
    override val trustedCaptureForAuthentication: Boolean = true

    companion object {
        private const val TAG = "TensorFlowFaceUnlockManager"
        const val IS_ENROLLMENT_KEY = "is_enrollment"
        const val ENROLLMENT_TAG_KEY = "enrollment_tag"

        private const val TF_OD_API_INPUT_SIZE = 112
        private const val TF_OD_API_IS_QUANTIZED = false
        private const val TF_OD_API_MODEL_FILE = "tf_bio/mobile_face_net.tflite"

        private const val KEY_ERROR_ACTIVE_UNTIL_TIMESTAMP = "error_active_until_timestamp"

        @Volatile
        private var config: TensorFlowFaceConfig = TensorFlowFaceConfig()

        private val worker = newSoftwareBiometricWorker("FaceUnlockWorker")
        private val sessionOwner = FaceSessionOwner()

        private val configurationObserverRegistered = AtomicBoolean(false)
        private val prefetchLock = Any()
        private var prefetchJob: Job? = null

        init {
            prefetchLocalizationStrings()
        }

        private fun prefetchLocalizationStrings() {
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

                scheduleLocalizationPrefetch(stringIds)
                if (configurationObserverRegistered.compareAndSet(false, true)) {
                    ExecutorHelper.post {
                        AndroidContext.configurationLiveData.observeForever {
                            scheduleLocalizationPrefetch(stringIds)
                        }
                    }
                }
            } catch (e: Throwable) {
                LogCat.logException(e)
            }
        }

        private fun scheduleLocalizationPrefetch(stringIds: Array<Int>) {
            synchronized(prefetchLock) {
                prefetchJob?.cancel()
                prefetchJob = ExecutorHelper.scope.launch {
                    try {
                        LocalizationHelper.prefetch(AndroidContext.appContext, *stringIds)
                    } catch (e: Throwable) {
                        LogCat.logException(e, TAG)
                    }
                }
            }
        }

        fun setTensorFlowFaceConfig(tensorFlowFaceConfig: TensorFlowFaceConfig) {
            config = tensorFlowFaceConfig
        }

        private fun sanitizeEnrollmentTag(tag: String?): String? {
            return tag
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.replace(Regex("[^A-Za-z0-9_.-]"), "_")
                ?.take(MAX_ENROLLMENT_TAG_LENGTH)
        }

        private const val MAX_ENROLLMENT_TAG_LENGTH = 80
    }

    private val effectiveConfig: EffectiveTensorFlowFaceConfig by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TfLiteBackendHelper.resolveEffectiveConfig(config).also {
            LogCat.log(TAG, "Effective config: $it")
        }
    }

    private val recognitionModelAvailable: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        hasAssetFile(context.assets, TF_OD_API_MODEL_FILE)
    }

    @Volatile private var frameProvider: IFrameProvider = RealCameraProvider(context)
    @Volatile private var pendingOperation: SoftwareBiometricWorkSession? = null
    private var cancelPendingOperation: (() -> Unit)? = null
    private var frameSession: FaceCaptureSession? = null
    private var frameTicket: Long? = null
    private var runningProvider: IFrameProvider? = null
    private val isSessionActive = AtomicBoolean(false)
    private val spoofWindow by lazy {
        FaceAntiSpoofingWindow(
            effectiveConfig.antiSpoofingWindowSize,
            effectiveConfig.antiSpoofingMinFramesToDecide,
            effectiveConfig.antiSpoofingScoreThreshold
        )
    }
    private var antiSpoofCandidateId: String? = null
    private var processedFrameCounter = 0
    private var consecutiveMatchCounter = 0
    private var lastMatchedId: String? = null
    private val faceChallengeRandom = SecureRandom()
    private var faceChallengeSessionNonce: Long? = null
    private var faceChallengeActions: List<FaceChallengeAction> = emptyList()
    private var faceChallengeIndex = 0
    private var faceChallengeStepStartedAtMs = 0L
    private var faceChallengeRejectedAttempts = 0

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
    private var isEnrolling: Boolean = false
    private var enrollmentTag: String = ""

    private val lockoutPolicy: LockoutPolicy
        get() = LockoutPolicy(
            maxFailedAttemptsBeforeLockout = effectiveConfig.maxFailedAttemptsBeforeLockout,
            maxTemporaryLockoutsBeforePermanent = effectiveConfig.maxTemporaryLockoutsBeforePermanent,
            lockoutDurationMs = effectiveConfig.lockoutDurationMs
        )

    @Synchronized
    fun setFrameProvider(provider: IFrameProvider) {
        cancelPendingOperation?.invoke()
        worker.execute { stopAuthentication() }
        this.frameProvider = provider
    }

    private fun checkLockoutState(): Int? {
        if (!frameProvider.isHardwareSupported()) {
            return CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        }

        return getStoredLockoutError(
            getProtectedPreferences(TFLiteObjectDetectionAPIModel.STORAGE_NAME),
            lockoutPolicy
        )
    }

    override fun getLockoutError(): Int? = checkLockoutState()

    private fun handleFailedAttempt() {
        commitSessionState {
            recordFailedAttempt(
                getProtectedPreferences(TFLiteObjectDetectionAPIModel.STORAGE_NAME), lockoutPolicy
            )
        }
    }

    private fun isErrorActive(): Boolean {
        val prefs = getProtectedPreferences(TFLiteObjectDetectionAPIModel.STORAGE_NAME)
        val activeUntil = prefs.getLong(KEY_ERROR_ACTIVE_UNTIL_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        if (activeUntil <= now) {
            if (activeUntil != 0L) {
                commitSessionState { prefs.edit { remove(KEY_ERROR_ACTIVE_UNTIL_TIMESTAMP) } }
            }
            return false
        }
        return true
    }

    private fun setErrorActive(durationMs: Long = effectiveConfig.errorCooldownMs) {
        commitSessionState {
            getProtectedPreferences(TFLiteObjectDetectionAPIModel.STORAGE_NAME).edit {
                putLong(KEY_ERROR_ACTIVE_UNTIL_TIMESTAMP, System.currentTimeMillis() + durationMs)
            }
        }
    }

    private fun <T> commitSessionState(action: () -> T): T? {
        val session = frameSession ?: return null
        val ticket = frameTicket
        return if (ticket == null) session.operation.runIfActive(action)
        else session.commit(ticket, action)
    }

    private fun stopAuthentication() {
        if (!isSessionActive.compareAndSet(true, false)) return

        clearAntiSpoofingWindow()

        try {
            (runningProvider as? CaptureContinuityProvider)?.setCaptureDiscontinuityListener(null)
            runningProvider?.stop()
            runningProvider = null
        } catch (e: Throwable) {
            LogCat.logException(e)
        }

        processedFrameCounter = 0
        consecutiveMatchCounter = 0
        lastMatchedId = null
        faceChallengeActions = emptyList()
        faceChallengeIndex = 0
        faceChallengeSessionNonce = null
        faceChallengeStepStartedAtMs = 0L
        faceChallengeRejectedAttempts = 0
        frameSession?.operation?.takeUnless { it.isActive }?.let(sessionOwner::release)
        authCallback = null
        isEnrolling = false
    }

    override fun getTimeoutMessage(): CharSequence {
        return LocalizationHelper.getLocalizedString(
            context,
            R.string.biometriccompat_tf_face_help_timeout
        )
    }

    override fun resetLockOut() {
        resetTemporaryLockoutState(getProtectedPreferences(TFLiteObjectDetectionAPIModel.STORAGE_NAME))
    }

    override fun resetPermanentLockOut() {
        resetPermanentLockoutState(getProtectedPreferences(TFLiteObjectDetectionAPIModel.STORAGE_NAME))
    }

    override fun getPermissions(): List<String> = listOf(Manifest.permission.CAMERA)
    override val biometricType: BiometricType = BiometricType.BIOMETRIC_FACE

    override fun prepareForAuthentication(callback: PreparationCallback) {
        val mainHandler = Handler(Looper.getMainLooper())
        ExecutorHelper.startOnBackground {
            try {
                if (!recognitionModelAvailable) {
                    mainHandler.post {
                        callback.onPreparationError(
                            CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                            LocalizationHelper.getLocalizedString(
                                context,
                                R.string.biometriccompat_tf_face_help_model_not_available
                            )
                        )
                    }
                    return@startOnBackground
                }
                val recognition = detector
                if (recognition == null || faceDetector == null || !frameProvider.isHardwareSupported()) {
                    mainHandler.post {
                        callback.onPreparationError(
                            CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                            LocalizationHelper.getLocalizedString(
                                context,
                                R.string.biometriccompat_tf_face_help_model_not_available
                            )
                        )
                    }
                    return@startOnBackground
                }

                val recognitionReady = (recognition as? TFLiteObjectDetectionAPIModel)
                    ?.waitUntilReady() ?: true
                if (!recognitionReady) {
                    mainHandler.post {
                        callback.onPreparationError(
                            CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                            LocalizationHelper.getLocalizedString(
                                context,
                                R.string.biometriccompat_tf_face_help_model_not_available
                            )
                        )
                    }
                    return@startOnBackground
                }

                if (antiSpoofingEnabled) {
                    val liveness = antiSpoofing
                    if (liveness != null && !liveness.waitUntilReady()) {
                        mainHandler.post {
                            callback.onPreparationError(
                                CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                                LocalizationHelper.getLocalizedString(
                                    context,
                                    R.string.biometriccompat_tf_face_help_model_not_available
                                )
                            )
                        }
                        return@startOnBackground
                    }
                }

                mainHandler.post { callback.onPrepared() }
            } catch (e: Throwable) {
                LogCat.logException(e)
                mainHandler.post {
                    callback.onPreparationError(
                        CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                        LocalizationHelper.getLocalizedString(
                            context,
                            R.string.biometriccompat_tf_face_help_model_not_available
                        )
                    )
                }
            }
        }
    }

    override fun isHardwareDetected(): Boolean {
        return recognitionModelAvailable && frameProvider.isHardwareCapabilityAvailable()
    }

    override fun hasEnrolledBiometric(): Boolean {
        val json = getProtectedPreferences(TFLiteObjectDetectionAPIModel.STORAGE_NAME)
            .getString(REGISTERED_TEMPLATES_PREF_KEY, null)
        return hasUsableFaceEnrollment(
            json,
            dev.skomlach.common.permissions.PermissionUtils.INSTANCE.hasSelfPermissions(Manifest.permission.CAMERA)
        )
    }
    override fun getManagers(): Set<Any> = emptySet()

    @Synchronized
    override fun remove(extra: Bundle?) {
        sessionOwner.revoke {
            detector?.delete(extra?.getString(ENROLLMENT_TAG_KEY))
        }
    }

    override fun getEnrollBundle(name: String?): Bundle {
        val registeredTemplates = countRegisteredTemplates(
            getProtectedPreferences(TFLiteObjectDetectionAPIModel.STORAGE_NAME)
                .getString(REGISTERED_TEMPLATES_PREF_KEY, null)
        )
        return Bundle().apply {
            putBoolean(IS_ENROLLMENT_KEY, true)
            putString(
                ENROLLMENT_TAG_KEY,
                sanitizeEnrollmentTag(name) ?: "face${registeredTemplates + 1}"
            )
        }
    }

    override fun getEnrolls(): Collection<String> = readStoredFaceEnrollmentIds(
        getProtectedPreferences(TFLiteObjectDetectionAPIModel.STORAGE_NAME)
            .getString(REGISTERED_TEMPLATES_PREF_KEY, null)
    )

    override fun getEnrollmentSnapshot(): SoftwareBiometricEnrollment =
        SoftwareBiometricEnrollment.read { getEnrolls() }

    @Synchronized
    override fun authenticate(
        crypto: CryptoObject?,
        flags: Int,
        cancel: CancellationSignal?,
        callback: AuthenticationCallback?,
        handler: Handler?,
        extra: Bundle?
    ) {
        val operation = SoftwareBiometricWorkSession().also { pendingOperation = it }
        val session = FaceCaptureSession(operation)
        val resultHandler = handler ?: Handler(Looper.getMainLooper())
        val workerCallback = SoftwareBiometricWorkerCallback(operation, resultHandler, callback) {
            sessionOwner.release(operation)
            synchronized(this) {
                if (pendingOperation === operation) {
                    pendingOperation = null
                    cancelPendingOperation = null
                }
            }
        }
        val arguments = extra?.let(::Bundle)
        val cancelAttempt = {
            workerCallback.onAuthenticationCancelled()
            sessionOwner.release(operation)
            worker.execute { if (frameSession === session) stopAuthentication() }
        }
        cancelPendingOperation = cancelAttempt
        sessionOwner.claim(operation, cancelAttempt)
        cancel?.setOnCancelListener(cancelAttempt)
        worker.execute {
            stopAuthentication()
            if (!operation.isActive) {
                sessionOwner.release(operation)
                return@execute
            }
            frameSession = session
            authCallback = workerCallback
            try {
                authenticateWithStorage(arguments)
            } catch (error: ProtectedStorageUnavailableException) {
                onProtectedStorageUnavailable(error)
            } catch (error: Exception) {
                LogCat.logException(error)
                workerCallback.onAuthenticationError(CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE, getTimeoutMessage())
                stopAuthentication()
            } catch (error: LinkageError) {
                LogCat.logException(error)
                workerCallback.onAuthenticationError(CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE, getTimeoutMessage())
                stopAuthentication()
            }
        }
    }

    private fun authenticateWithStorage(extra: Bundle?) {
        isSessionActive.set(true)
        clearAntiSpoofingWindow()
        processedFrameCounter = 0
        consecutiveMatchCounter = 0
        lastMatchedId = null

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
            return
        }

        isEnrolling = extra?.getBoolean(IS_ENROLLMENT_KEY, false) ?: false
        val registeredTemplates = countRegisteredTemplates(
            getProtectedPreferences(TFLiteObjectDetectionAPIModel.STORAGE_NAME)
                .getString(REGISTERED_TEMPLATES_PREF_KEY, null)
        )
        enrollmentTag = sanitizeEnrollmentTag(extra?.getString(ENROLLMENT_TAG_KEY))
            ?: "face${registeredTemplates + 1}"

        val usesRealCameraProvider = frameProvider is RealCameraProvider
        when (
            resolveTensorFlowFacePreflightIssue(
                isHardwareDetected = isHardwareDetected(),
                usesRealCameraProvider = usesRealCameraProvider,
                isCameraBlocked = usesRealCameraProvider && SensorPrivacyCheck.isCameraBlocked(),
                isCameraInUse = usesRealCameraProvider && SensorPrivacyCheck.isCameraInUse(),
                isEnrolling = isEnrolling,
                hasEnrolledBiometric = hasEnrolledBiometric(),
                antiSpoofingAvailable = isAntiSpoofingEnabledForFlow() && antiSpoofing != null,
                requireAntiSpoofing = effectiveConfig.base.requireAntiSpoofingForAuthentication,
                requireRealCameraProvider = effectiveConfig.base.requireRealCameraProviderForAuthentication
            )
        ) {
            TensorFlowFacePreflightIssue.HARDWARE_MISSING -> {
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

            TensorFlowFacePreflightIssue.CAMERA_BLOCKED -> {
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

            TensorFlowFacePreflightIssue.CAMERA_IN_USE -> {
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

            TensorFlowFacePreflightIssue.NO_ENROLLED_BIOMETRIC -> {
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

            TensorFlowFacePreflightIssue.ANTI_SPOOFING_UNAVAILABLE -> {
                onAuthenticationError(
                    CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_tf_face_help_model_not_available
                    )
                )
                stopAuthentication()
                return
            }

            TensorFlowFacePreflightIssue.UNTRUSTED_CAPTURE_PROVIDER -> {
                onAuthenticationError(
                    CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_tf_face_help_untrusted_capture
                    )
                )
                stopAuthentication()
                return
            }

            null -> {
            }
        }

        restartFaceChallenge()
        val session = frameSession ?: return
        if (!canStartAuthenticationSession()) return
        val provider = frameProvider
        runningProvider = provider
        (provider as? CaptureContinuityProvider)?.setCaptureDiscontinuityListener {
            onCaptureDiscontinuity(session)
        }
        provider.start(
            faceDetector!!,
            { bitmap, faces -> onFrameReceived(session, bitmap, faces) },
            { code, msg ->
                worker.execute {
                    if (frameSession === session && session.operation.isActive && isSessionActive.get()) {
                        onAuthenticationError(code, msg)
                        stopAuthentication()
                    }
                }
            }
        )
    }

    private fun restartFaceChallenge() {
        faceChallengeActions = emptyList()
        faceChallengeIndex = 0
        faceChallengeSessionNonce = null
        faceChallengeStepStartedAtMs = 0L
        faceChallengeRejectedAttempts = 0
        if (!isEnrolling && effectiveConfig.base.faceChallengeEnabled) {
            val nonce = faceChallengeRandom.nextLong()
            faceChallengeSessionNonce = nonce
            faceChallengeActions = faceChallengeSessionNonce
                ?.let {
                    generateFaceChallenge(
                        sessionNonce = it,
                        length = effectiveConfig.base.faceChallengeLength
                    )
                }
                .orEmpty()
            faceChallengeIndex = 0
            faceChallengeStepStartedAtMs = SystemClock.elapsedRealtime()
            faceChallengeRejectedAttempts = 0
            emitFaceChallengeInstruction()
        }
    }

    private fun onCaptureDiscontinuity(session: FaceCaptureSession) {
        // This invalidates a frame even when the inference worker is busy.
        session.discontinue()
        worker.execute {
            if (frameSession !== session || !session.operation.isActive || !isSessionActive.get()) return@execute
            clearAntiSpoofingWindow()
            processedFrameCounter = 0
            consecutiveMatchCounter = 0
            lastMatchedId = null
            restartFaceChallenge()
            authCallback?.onAuthenticationHelp(CUSTOM_BIOMETRIC_ACQUIRED_PARTIAL,
                LocalizationHelper.getLocalizedString(context, R.string.biometriccompat_tf_face_help_model_not_detected))
        }
    }

    private fun onFrameReceived(session: FaceCaptureSession, fullBitmap: Bitmap, faces: List<Face>) {
        if (faces.isEmpty()) {
            onCaptureDiscontinuity(session)
            if (!fullBitmap.isRecycled) fullBitmap.recycle()
            return
        }
        val ticket = session.acquireFrame()
        if (ticket == null) {
            if (!fullBitmap.isRecycled) fullBitmap.recycle()
            return
        }
        worker.execute {
            try {
                useOwnedFrame(fullBitmap, { if (!it.isRecycled) it.recycle() }) { bitmap ->
                    if (frameSession === session && session.owns(ticket) && isSessionActive.get()) {
                        frameTicket = ticket
                        processFaces(bitmap, faces)
                    }
                }
            } catch (error: ProtectedStorageUnavailableException) {
                onProtectedStorageUnavailable(error)
            } catch (error: Exception) {
                LogCat.logException(error)
                onCaptureDiscontinuity(session)
            } catch (error: LinkageError) {
                LogCat.logException(error)
                onAuthenticationError(CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE, getTimeoutMessage().toString())
                stopAuthentication()
            } finally {
                frameTicket = null
                session.releaseFrame()
            }
        }
    }
    private fun clearAntiSpoofingWindow() {
        spoofWindow.reset()
        antiSpoofCandidateId = null
    }

    private fun isAntiSpoofingEnabledForFlow(): Boolean = antiSpoofingEnabled &&
        effectiveConfig.antiSpoofingMode != AntiSpoofingMode.OFF &&
        if (isEnrolling) effectiveConfig.antiSpoofingOnEnrollment else effectiveConfig.antiSpoofingOnAuthentication

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

    private fun readAntiSpoofingScore(bitmap: Bitmap): Float? {
        val engine = antiSpoofing ?: return null
        return try {
            engine.antiSpoofing(bitmap)
        } catch (e: Throwable) {
            LogCat.logException(e, TAG)
            null
        }
    }

    private fun checkFaceLiveness(score: Float?): FaceAttemptResult? {
        val required = effectiveConfig.base.requireAntiSpoofingForAuthentication
        val decision = spoofWindow.add(score)
        if (isFaceLivenessAccepted(decision, required)) return null
        if (decision == FaceAntiSpoofingDecision.PENDING) return FaceAttemptResult.MatchInProgress
        clearAntiSpoofingWindow()
        consecutiveMatchCounter = 0
        lastMatchedId = null
        if (decision == FaceAntiSpoofingDecision.SPOOF) {
            handleSpoofFailure()
            return FaceAttemptResult.Spoof
        }
        onAuthenticationError(
            CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
            LocalizationHelper.getLocalizedString(context, R.string.biometriccompat_tf_face_help_model_not_available)
        )
        return FaceAttemptResult.FatalError
    }

    private fun onAuthenticationError(code: Int, msg: String) {
        try {
            val ticket = frameTicket
            if (ticket != null && frameSession?.owns(ticket) != true) return
            setErrorActive()
            authCallback?.onAuthenticationError(code, msg)
            // The callback is terminal even for a direct manager user without a module wrapper.
            stopAuthentication()
        } catch (error: ProtectedStorageUnavailableException) {
            onProtectedStorageUnavailable(error)
        }
    }

    private fun onProtectedStorageUnavailable(error: ProtectedStorageUnavailableException) {
        LogCat.logException(error, TAG)
        val callback = authCallback
        // Error delivery must not read/write the cooldown in the unavailable store again.
        stopAuthentication()
        callback?.onAuthenticationError(CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
            LocalizationHelper.getLocalizedString(context,
                R.string.biometriccompat_tf_face_help_model_not_available))
    }

    private fun canStartAuthenticationSession(): Boolean {
        return shouldStartTensorFlowFaceSession(isSessionActive =
            isSessionActive.get() && frameSession?.operation?.isActive == true)
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

    private fun handleSpoofFailure() {
        if (shouldCountFaceSpoofFailure(isEnrolling)) {
            handleFailedAttempt()
        }
        onAuthenticationError(
            CUSTOM_BIOMETRIC_ERROR_NO_SPACE,
            LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_tf_face_help_model_fake_face_detected
            )
        )
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

        if (!isEnrolling && !isFaceChallengeSatisfied(face)) {
            return FaceAttemptResult.MatchInProgress
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

            val needsLiveness = effectiveConfig.base.requireAntiSpoofingForAuthentication ||
                isAntiSpoofingEnabledForFlow()
            val antiSpoofStageBefore = resolveAntiSpoofingStage(
                frameNumber = processedFrameCounter,
                consecutiveMatches = consecutiveMatchCounter,
                candidateMatched = false
            )
            val measuredBefore = needsLiveness && antiSpoofStageBefore == AntiSpoofingStage.BEFORE_RECOGNITION
            val scoreBefore = if (measuredBefore) readAntiSpoofingScore(livenessCrop) else null

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

            if (isEnrolling) {
                if (needsLiveness) {
                    // Enrollment itself is terminal: frame stride cannot skip this security gate.
                    checkFaceLiveness(if (measuredBefore) scoreBefore else readAntiSpoofingScore(livenessCrop))
                        ?.let { return it }
                }

                commitSessionState {
                    result.crop = alignedFace.copy(alignedFace.config ?: Bitmap.Config.ARGB_8888, false)
                    detector?.register(enrollmentTag, result)
                    resetPermanentLockOut()
                } ?: return FaceAttemptResult.FatalError
                LogCat.logError(TAG, "processFaces onAuthenticationSucceeded (enroll)")
                authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                stopAuthentication()
                return FaceAttemptResult.Success
            }

            val distance = result.distance ?: return FaceAttemptResult.InvalidFace
            val id = result.id
            val matched = distance < effectiveConfig.maxDistanceThreshold

            if (!matched || id != antiSpoofCandidateId) {
                clearAntiSpoofingWindow()
                if (matched) antiSpoofCandidateId = id
            }

            val antiSpoofStageForMatch = resolveAntiSpoofingStage(
                frameNumber = processedFrameCounter,
                consecutiveMatches = consecutiveMatchCounter,
                candidateMatched = matched
            )
            val attempt = evaluateFaceAuthenticationAttempt(
                state = FaceAuthenticationAttemptState(lastMatchedId, consecutiveMatchCounter),
                candidateId = id,
                distance = distance,
                maximumDistance = effectiveConfig.maxDistanceThreshold,
                requiredConsecutiveMatches = effectiveConfig.requiredConsecutiveMatches
            )
            consecutiveMatchCounter = attempt.state.consecutiveMatches
            lastMatchedId = attempt.state.matchedId
            if (matched && needsLiveness && (measuredBefore ||
                    antiSpoofStageForMatch == AntiSpoofingStage.AFTER_CANDIDATE ||
                    attempt.outcome == FaceAuthenticationAttemptOutcome.SUCCESS)) {
                // Always measure the terminal candidate, even on a frame skipped by the stride.
                checkFaceLiveness(if (measuredBefore) scoreBefore else readAntiSpoofingScore(livenessCrop))
                    ?.let { return it }
            }
            when (attempt.outcome) {
                FaceAuthenticationAttemptOutcome.SUCCESS -> {
                    commitSessionState { resetPermanentLockOut() } ?: return FaceAttemptResult.FatalError
                    LogCat.logError(TAG, "processFaces onAuthenticationSucceeded (auth)")
                    authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                    stopAuthentication()
                    return FaceAttemptResult.Success
                }
                FaceAuthenticationAttemptOutcome.MATCH_IN_PROGRESS -> return FaceAttemptResult.MatchInProgress
                FaceAuthenticationAttemptOutcome.RETRY -> Unit
            }

            return FaceAttemptResult.NoMatch(distance)
        } finally {
            if (!alignedFace.isRecycled) alignedFace.recycle()
            if (!livenessCrop.isRecycled) livenessCrop.recycle()
        }
    }

    private fun isFaceChallengeSatisfied(face: Face): Boolean {
        if (faceChallengeActions.isEmpty()) return true

        val nowMs = SystemClock.elapsedRealtime()
        when (
            evaluateFaceChallengeGuard(
                stepStartedAtMs = faceChallengeStepStartedAtMs,
                nowMs = nowMs,
                rejectedAttempts = faceChallengeRejectedAttempts,
                maxRejectedAttempts = effectiveConfig.base.faceChallengeMaxRejectedAttempts,
                stepTimeoutMs = effectiveConfig.base.faceChallengeStepTimeoutMs
            )
        ) {
            FaceChallengeGuardDecision.TIMEOUT -> {
                onAuthenticationError(
                    CUSTOM_BIOMETRIC_ERROR_TIMEOUT,
                    getTimeoutMessage().toString()
                )
                stopAuthentication()
                return false
            }

            FaceChallengeGuardDecision.ATTEMPTS_EXCEEDED -> {
                onAuthenticationError(
                    CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_tf_face_help_model_retry
                    )
                )
                stopAuthentication()
                return false
            }

            FaceChallengeGuardDecision.ALLOW -> Unit
        }

        val tolerance = effectiveConfig.base.faceChallengeToleranceDegrees
        val targetMagnitude = min(
            effectiveConfig.base.faceChallengeYawDegrees,
            (effectiveConfig.maxHeadAngleY - tolerance).coerceAtLeast(1f)
        )
        return when (
            advanceFaceChallenge(
                expected = faceChallengeActions,
                index = faceChallengeIndex,
                yawDegrees = face.headEulerAngleY,
                toleranceDegrees = tolerance,
                targetYawMagnitudeDegrees = targetMagnitude
            )
        ) {
            FaceChallengeDecision.IN_PROGRESS -> {
                faceChallengeIndex++
                faceChallengeStepStartedAtMs = nowMs
                faceChallengeRejectedAttempts = 0
                emitFaceChallengeInstruction()
                false
            }

            FaceChallengeDecision.COMPLETE -> {
                faceChallengeActions = emptyList()
                faceChallengeIndex = 0
                faceChallengeStepStartedAtMs = 0L
                faceChallengeRejectedAttempts = 0
                true
            }

            FaceChallengeDecision.REJECTED -> {
                faceChallengeRejectedAttempts++
                emitFaceChallengeInstruction()
                false
            }
        }
    }

    private fun emitFaceChallengeInstruction() {
        val action = faceChallengeActions.getOrNull(faceChallengeIndex) ?: return
        val actionTextRes = when (action) {
            FaceChallengeAction.CENTER -> R.string.biometriccompat_tf_face_challenge_center
            FaceChallengeAction.LEFT -> R.string.biometriccompat_tf_face_challenge_left
            FaceChallengeAction.RIGHT -> R.string.biometriccompat_tf_face_challenge_right
        }
        val actionText = LocalizationHelper.getLocalizedString(context, actionTextRes)
        authCallback?.onAuthenticationHelp(
            CUSTOM_BIOMETRIC_ACQUIRED_PARTIAL,
            LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_tf_face_challenge_instruction,
                actionText
            )
        )
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

        return transformFaceCrop(originalBitmap, sourceBitmap, { it.recycle() }) {
            val leftEyePos = leftEye.position
            val rightEyePos = rightEye.position
            val deltaX = rightEyePos.x - leftEyePos.x
            val deltaY = rightEyePos.y - leftEyePos.y
            val angle = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()
            val eyeDistance = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
            if (eyeDistance <= 0f) return@transformFaceCrop null

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
        return transformFaceCrop(originalBitmap, croppedBitmap, { it.recycle() }) {
            val scaled = it.scale(outputSize, outputSize)
            // The returned crop belongs to the consumer, never to the camera-frame owner.
            if (scaled === originalBitmap) scaled.copy(scaled.config ?: Bitmap.Config.ARGB_8888, false)
            else scaled
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
