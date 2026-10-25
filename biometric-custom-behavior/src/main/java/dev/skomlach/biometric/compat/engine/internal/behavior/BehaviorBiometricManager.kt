package dev.skomlach.biometric.compat.engine.internal.behavior

import dev.skomlach.common.storage.ProtectedStorageUnavailableException

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricEnrollment
import dev.skomlach.biometric.compat.custom.SoftwareBiometricSecurityProfile
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.biometric.custom.behavior.R
import dev.skomlach.common.translate.LocalizationHelper
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkSession
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkerCallback

class BehaviorBiometricManager(
    private val context: Context,
    private val store: BehaviorTemplateStore = BehaviorTemplateStore()
) : AbstractSoftwareBiometricManager() {

    override val biometricType: BiometricType = BiometricType.BIOMETRIC_BEHAVIOR
    override val priority: Int = PRIORITY_BELOW_SYSTEM_HARDWARE
    override val securityProfile: SoftwareBiometricSecurityProfile =
        SoftwareBiometricSecurityProfile.passiveCompatibility(BiometricType.BIOMETRIC_BEHAVIOR)

    private var activeSession: SoftwareBiometricWorkSession? = null
    private var currentHandler: Handler = Handler(Looper.getMainLooper())
    private var resultRunnable: Runnable? = null
    private val prefs by lazy {
        dev.skomlach.common.storage.SharedPreferenceProvider.getProtectedPreferences(
            LOCKOUT_STORAGE_NAME
        )
    }

    override fun getTimeoutMessage(): CharSequence =
        localized(R.string.biometriccompat_behavior_help_timeout)

    override fun resetLockOut() {
        resetTemporaryLockoutState(prefs)
    }

    override fun resetPermanentLockOut() {
        resetPermanentLockoutState(prefs)
    }

    override fun getPermissions(): List<String> = emptyList()

    override fun getLockoutError(): Int? = getStoredLockoutError(prefs, LOCKOUT_POLICY)

    override fun isHardwareDetected(): Boolean = true

    override fun hasEnrolledBiometric(): Boolean = store.hasTemplate()

    override fun getEnrollmentSnapshot(): SoftwareBiometricEnrollment =
        SoftwareBiometricEnrollment.read { getEnrolls() }

    override fun getManagers(): Set<Any> = setOf(this)

    override fun remove(extra: Bundle?) {
        store.remove(extra?.getString(ENROLLMENT_TAG_KEY))
    }

    override fun getEnrollBundle(name: String?): Bundle {
        return Bundle().apply {
            putBoolean(IS_ENROLLMENT_KEY, true)
            putString(ENROLLMENT_TAG_KEY, store.sanitizeTag(name))
        }
    }

    override fun getEnrolls(): Collection<String> = store.templateNames()

    @Synchronized
    override fun authenticate(
        crypto: CryptoObject?,
        flags: Int,
        cancel: CancellationSignal?,
        callback: AuthenticationCallback?,
        handler: Handler?,
        extra: Bundle?
    ) {
        cancelActiveSession()
        currentHandler = handler ?: Handler(Looper.getMainLooper())
        val session = store.newWorkSession().also { activeSession = it }
        val ownedCallback = SoftwareBiometricWorkerCallback(session, currentHandler, callback)
        cancel?.setOnCancelListener { ownedCallback.onAuthenticationCancelled() }
        if (!session.isActive) return
        if (BiometricPromptCompat.hasDetectedRuntimeHooks()) {
            finishWithError(ownedCallback, CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                localized(R.string.biometriccompat_behavior_help_unavailable))
            return
        }
        try {
            authenticateWithStorage(crypto, ownedCallback, extra, session)
        } catch (error: ProtectedStorageUnavailableException) {
            e(error, "Behavior protected storage unavailable")
            finishWithError(ownedCallback, CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                localized(R.string.biometriccompat_behavior_help_unavailable))
        }
    }

    // Keep the authentication checks together under the caller's storage error boundary.
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    private fun authenticateWithStorage(
        crypto: CryptoObject?,
        callback: AuthenticationCallback?,
        extra: Bundle?,
        session: SoftwareBiometricWorkSession
    ) {
        if (!session.isActive) return

        val lockoutError = getLockoutError()
        if (lockoutError != null) {
            finishWithError(callback, lockoutError, lockoutMessage(lockoutError))
            return
        }

        if (!isHardwareDetected()) {
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                localized(R.string.biometriccompat_behavior_help_unavailable)
            )
            return
        }

        val isEnrollment = extra?.getBoolean(IS_ENROLLMENT_KEY, false) == true
        val sessionNonce = extra
            ?.takeIf { it.containsKey(EXTRA_BEHAVIOR_SESSION_NONCE) }
            ?.getLong(EXTRA_BEHAVIOR_SESSION_NONCE)
        val captureToken = sessionNonce?.let(BehaviorCaptureSessionRegistry::token)
        // A sealed handle always resolves to its private snapshot, never to caller-modified arrays.
        val sample = if (sessionNonce != null) {
            BehaviorCaptureSessionRegistry.consumeSample(sessionNonce, SystemClock.elapsedRealtime(), isEnrollment)
        } else BehaviorSample.fromBundle(extra)
        if (sample == null) {
            e("BehaviorBiometricManager.authenticate sample=missing_or_incomplete")
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                localized(R.string.biometriccompat_behavior_help_sample_missing)
            )
            return
        }
        if (sessionNonce != null) {
            if (captureToken == null) {
                finishWithError(
                    callback,
                    CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                    localized(R.string.biometriccompat_behavior_error_capture_invalid)
                )
                return
            }
            val inputDecision = evaluateBehaviorSampleIntegrity(
                sample, 0L, captureToken.relativeUptime(SystemClock.uptimeMillis()), MAX_INTER_EVENT_GAP_MS)
            if (inputDecision != BehaviorInputIntegrityDecision.ACCEPT) {
                finishWithError(
                    callback,
                    CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                    localized(R.string.biometriccompat_behavior_error_capture_invalid)
                )
                return
            }
        }
        val qualityIssue = sample.qualityIssue()
        val metrics = sample.metrics().toLogString()
        if (qualityIssue != BehaviorQualityIssue.NONE) {
            e("BehaviorBiometricManager.authenticate quality=$qualityIssue metrics=$metrics")
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                qualityMessage(qualityIssue)
            )
            return
        }

        val replayDecision = session.runIfActive {
            store.checkAndRememberSample(sample, SystemClock.elapsedRealtime(), isEnrollment)
        } ?: return
        if (replayDecision != BehaviorReplayDecision.ACCEPT) {
            e("BehaviorBiometricManager.authenticate capture=$replayDecision")
            finishWithError(callback, CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                localized(R.string.biometriccompat_behavior_error_capture_invalid))
            return
        }

        if (isEnrollment) {
            val tag = try {
                session.runIfActive {
                    store.save(extra?.getString(ENROLLMENT_TAG_KEY), sample).also {
                        resetTemporaryLockoutState(prefs)
                    }
                } ?: return
            } catch (_: IllegalArgumentException) {
                // A single externally supplied capture can exceed the bounded storage format.
                finishWithError(callback, CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                    localized(R.string.biometriccompat_behavior_error_capture_invalid))
                return
            }
            e("BehaviorBiometricManager.enroll quality=OK metrics=$metrics")
            finishWithSuccess(
                callback,
                crypto,
                localized(R.string.biometriccompat_behavior_help_enrolled, tag)
            )
            return
        }

        val templates = store.loadTemplates()
        if (templates.isEmpty()) {
            e("BehaviorBiometricManager.authenticate mode=${sample.mode} templates=0")
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC,
                localized(R.string.biometriccompat_behavior_help_not_registered)
            )
            return
        }

        val bestMatch = templates
            .groupBy({ it.first }, { it.second })
            .values
            .map { enrolledSamples -> matchTemplates(enrolledSamples, sample) }
            .maxByOrNull { it.score }
            ?: TemplateMatch(0f)

        val threshold = thresholdFor(sample.mode)
        e(
            "BehaviorBiometricManager.authenticate templates=${templates.size} " +
                "score=${bestMatch.score} threshold=$threshold metrics=$metrics"
        )
        if (bestMatch.score >= threshold) {
            session.runIfActive { resetTemporaryLockoutState(prefs) } ?: return
            finishWithSuccess(
                callback,
                crypto,
                localized(R.string.biometriccompat_behavior_help_accepted)
            )
        } else {
            session.runIfActive { recordFailedAttempt(prefs, LOCKOUT_POLICY) } ?: return
            val updatedLockout = getLockoutError()
            if (updatedLockout != null) {
                finishWithError(callback, updatedLockout, lockoutMessage(updatedLockout))
            } else {
                finishWithError(
                    callback,
                    CUSTOM_BIOMETRIC_ERROR_VENDOR,
                    localized(R.string.biometriccompat_behavior_help_not_matched)
                )
            }
        }
    }

    private fun matchTemplates(
        enrolledSamples: List<BehaviorSample>,
        probe: BehaviorSample
    ): TemplateMatch {
        val scores = enrolledSamples
            .map { BehaviorScorer.scoreDetails(it, probe) }
            .filter { it.reason == BehaviorScoreReason.OK || it.total > 0f }
            .map { it.total }
            .sortedDescending()
        if (scores.isEmpty()) return TemplateMatch(0f)

        val topScores = scores.take(TOP_K_TEMPLATES)
        val weighted = topScores.withIndex().sumOf { (index, score) ->
            score.toDouble() * (TOP_K_TEMPLATES - index)
        }
        val weight = topScores.indices.sumOf { TOP_K_TEMPLATES - it }.toDouble()
        return TemplateMatch((weighted / weight).toFloat())
    }

    private fun thresholdFor(mode: BehaviorMode): Float {
        return when (mode) {
            BehaviorMode.TYPING -> TYPING_MATCH_THRESHOLD
            BehaviorMode.SIGNATURE -> SIGNATURE_MATCH_THRESHOLD
            BehaviorMode.COMBINED -> COMBINED_MATCH_THRESHOLD
        }
    }

    private fun qualityMessage(issue: BehaviorQualityIssue): CharSequence {
        return when (issue) {
            BehaviorQualityIssue.NONE -> localized(R.string.biometriccompat_behavior_help_sample_valid)
            BehaviorQualityIssue.TYPING_PHRASE_TOO_SHORT ->
                localized(R.string.biometriccompat_behavior_help_typing_phrase_short)
            BehaviorQualityIssue.TYPING_SAMPLE_TOO_SHORT ->
                localized(R.string.biometriccompat_behavior_help_typing_sample_short)
            BehaviorQualityIssue.TYPING_EVENT_MISMATCH ->
                localized(R.string.biometriccompat_behavior_help_typing_event_mismatch)
            BehaviorQualityIssue.TYPING_TIMING_TOO_FAST ->
                localized(R.string.biometriccompat_behavior_help_typing_too_fast)
            BehaviorQualityIssue.TYPING_TIMING_TOO_UNIFORM ->
                localized(R.string.biometriccompat_behavior_help_typing_too_uniform)
            BehaviorQualityIssue.SIGNATURE_SAMPLE_TOO_SHORT ->
                localized(R.string.biometriccompat_behavior_help_signature_sample_short)
            BehaviorQualityIssue.SIGNATURE_PATH_TOO_SHORT ->
                localized(R.string.biometriccompat_behavior_help_signature_path_short)
            BehaviorQualityIssue.SIGNATURE_SHAPE_TOO_SMALL ->
                localized(R.string.biometriccompat_behavior_help_signature_shape_small)
            BehaviorQualityIssue.SIGNATURE_SHAPE_TOO_SIMPLE ->
                localized(R.string.biometriccompat_behavior_help_signature_shape_simple)
            BehaviorQualityIssue.SIGNATURE_DUPLICATE_POINTS ->
                localized(R.string.biometriccompat_behavior_help_signature_duplicate_points)
        }
    }

    private fun finishWithSuccess(
        callback: AuthenticationCallback?,
        crypto: CryptoObject?,
        helpMessage: CharSequence
    ) {
        val session = activeSession ?: return
        resultRunnable = Runnable {
            if (session.isActive) {
                if (BiometricPromptCompat.hasDetectedRuntimeHooks()) {
                    finishWithError(callback, CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                        localized(R.string.biometriccompat_behavior_help_unavailable))
                    return@Runnable
                }
                callback?.onAuthenticationHelp(CUSTOM_BIOMETRIC_ACQUIRED_GOOD, helpMessage)
                callback?.onAuthenticationSucceeded(AuthenticationResult(crypto))
            }
        }.also {
            currentHandler.postDelayed(it, RESULT_DELAY_MS)
        }
    }

    private fun finishWithError(
        callback: AuthenticationCallback?,
        error: Int,
        message: CharSequence
    ) {
        callback?.onAuthenticationError(error, message)
    }

    private fun cancelActiveSession() {
        activeSession?.cancel()
        activeSession = null
        resultRunnable?.let { currentHandler.removeCallbacks(it) }
        resultRunnable = null
    }

    private fun lockoutMessage(error: Int): CharSequence {
        return when (error) {
            CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE -> localized(R.string.biometriccompat_behavior_help_unavailable)
            CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT ->
                localized(R.string.biometriccompat_behavior_help_lockout_permanent)
            CUSTOM_BIOMETRIC_ERROR_LOCKOUT ->
                localized(R.string.biometriccompat_behavior_help_lockout)
            else -> localized(R.string.biometriccompat_behavior_help_unavailable)
        }
    }

    private fun localized(id: Int, vararg formatArgs: Any?): String {
        return LocalizationHelper.getLocalizedString(context, id, *formatArgs)
    }

    private companion object {
        const val IS_ENROLLMENT_KEY = "is_enrollment"
        const val ENROLLMENT_TAG_KEY = "enrollment_tag"
        const val LOCKOUT_STORAGE_NAME = "behavior_lockout"
        const val TYPING_MATCH_THRESHOLD = 0.84f
        const val SIGNATURE_MATCH_THRESHOLD = 0.80f
        const val COMBINED_MATCH_THRESHOLD = 0.83f
        const val TOP_K_TEMPLATES = 3
        const val RESULT_DELAY_MS = 500L
        const val MAX_INTER_EVENT_GAP_MS = 2_000L

        val LOCKOUT_POLICY = LockoutPolicy(
            maxFailedAttemptsBeforeLockout = 5,
            maxTemporaryLockoutsBeforePermanent = 5,
            lockoutDurationMs = 30_000L
        )
    }
}

private data class TemplateMatch(
    val score: Float
)
