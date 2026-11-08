package dev.skomlach.biometric.compat.engine.internal.behavior

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
import dev.skomlach.biometric.compat.custom.newSoftwareBiometricWorker
import java.util.concurrent.Executor

class BehaviorBiometricManager internal constructor(
    private val context: Context,
    private val store: BehaviorTemplateStore,
    private val worker: Executor
) : AbstractSoftwareBiometricManager() {
    constructor(context: Context, store: BehaviorTemplateStore = BehaviorTemplateStore()) :
        this(context, store, WORKER)

    private data class Capture(
        val sample: BehaviorSample?,
        val enrollment: Boolean,
        val tag: String?,
        val requiresSeal: Boolean,
        val captureUptimeMs: Long?
    )

    override val biometricType: BiometricType = BiometricType.BIOMETRIC_BEHAVIOR
    override val priority: Int = PRIORITY_BELOW_SYSTEM_HARDWARE
    override val securityProfile: SoftwareBiometricSecurityProfile =
        SoftwareBiometricSecurityProfile.passiveCompatibility(BiometricType.BIOMETRIC_BEHAVIOR)

    private var activeSession: SoftwareBiometricWorkSession? = null
    private val prefs by lazy {
        dev.skomlach.common.storage.SharedPreferenceProvider.getProtectedPreferences(
            LOCKOUT_STORAGE_NAME
        )
    }

    override fun getTimeoutMessage(): CharSequence =
        localized(R.string.biometriccompat_behavior_help_timeout)

    override fun resetLockOut() {
        store.updateLockout { resetTemporaryLockoutState(prefs) }
    }

    override fun resetPermanentLockOut() {
        store.updateLockout { resetPermanentLockoutState(prefs) }
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
        activeSession?.cancel()
        val resultHandler = handler ?: Handler(Looper.getMainLooper())
        val session = store.newWorkSession().also { activeSession = it }
        val ownedCallback = SoftwareBiometricWorkerCallback(session, resultHandler, callback) {
            synchronized(this) { if (activeSession === session) activeSession = null }
        }
        cancel?.setOnCancelListener { ownedCallback.onAuthenticationCancelled() }
        if (!session.isActive) return
        if (BiometricPromptCompat.hasDetectedRuntimeHooks()) {
            finishWithError(ownedCallback, CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                localized(R.string.biometriccompat_behavior_help_unavailable))
            return
        }

        // Callers may clear their Bundle and arrays as soon as authenticate returns.
        val capture = snapshotCapture(extra)
        worker.execute {
            if (!session.isActive) return@execute
            try {
                check(!BiometricPromptCompat.hasDetectedRuntimeHooks()) { "Behavior authentication rejected by runtime hook policy" }
                authenticateWithStorage(crypto, ownedCallback, capture, session, resultHandler)
            } catch (error: Exception) {
                e(error, "Behavior authentication unavailable")
                finishWithError(ownedCallback, CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                    localized(R.string.biometriccompat_behavior_help_unavailable))
            }
        }
    }

    private fun snapshotCapture(extra: Bundle?): Capture {
        val enrollment = extra?.getBoolean(IS_ENROLLMENT_KEY, false) == true
        val nonce = extra?.takeIf { it.containsKey(EXTRA_BEHAVIOR_SESSION_NONCE) }?.getLong(EXTRA_BEHAVIOR_SESSION_NONCE)
        val token = nonce?.let(BehaviorCaptureSessionRegistry::token)
        // A sealed handle always resolves to its private snapshot, never to caller-modified arrays.
        val sample = if (nonce == null) BehaviorSample.fromBundle(extra) else
            BehaviorCaptureSessionRegistry.consumeSample(nonce, SystemClock.elapsedRealtime(), enrollment)
        return Capture(sample, enrollment, extra?.getString(ENROLLMENT_TAG_KEY), nonce != null,
            token?.relativeUptime(SystemClock.uptimeMillis()))
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    private fun authenticateWithStorage(
        crypto: CryptoObject?,
        callback: AuthenticationCallback,
        capture: Capture,
        session: SoftwareBiometricWorkSession,
        resultHandler: Handler
    ) {
        val lockoutError = session.runIfActive(::getLockoutError)
        if (!session.isActive) return
        if (lockoutError != null) {
            finishWithError(callback, lockoutError, lockoutMessage(lockoutError))
            return
        }

        val isEnrollment = capture.enrollment
        val sample = capture.sample
        if (sample == null) {
            e("BehaviorBiometricManager.authenticate sample=missing_or_incomplete")
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                localized(R.string.biometriccompat_behavior_help_sample_missing)
            )
            return
        }
        if (capture.requiresSeal) {
            val captureUptimeMs = capture.captureUptimeMs
            if (captureUptimeMs == null) {
                finishWithError(
                    callback,
                    CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                    localized(R.string.biometriccompat_behavior_error_capture_invalid)
                )
                return
            }
            val inputDecision = evaluateBehaviorSampleIntegrity(
                sample, 0L, captureUptimeMs, MAX_INTER_EVENT_GAP_MS)
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
                store.save(capture.tag, sample, session) ?: return
            } catch (_: IllegalArgumentException) {
                // A single externally supplied capture can exceed the bounded storage format.
                finishWithError(callback, CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                    localized(R.string.biometriccompat_behavior_error_capture_invalid))
                return
            }
            session.runIfActive { resetTemporaryLockoutState(prefs) } ?: return
            e("BehaviorBiometricManager.enroll quality=OK metrics=$metrics")
            finishWithSuccess(
                callback,
                crypto,
                session,
                resultHandler,
                localized(R.string.biometriccompat_behavior_help_enrolled, tag)
            )
            return
        }

        var templateCount = 0
        var bestMatch = TemplateMatch(0f)
        store.visitProfiles { _, samples ->
            if (!session.isActive) return@visitProfiles false
            templateCount += samples.size
            val match = matchTemplates(samples, sample)
            if (match.score > bestMatch.score) bestMatch = match
            true
        }
        if (!session.isActive) return
        if (templateCount == 0) {
            e("BehaviorBiometricManager.authenticate mode=${sample.mode} templates=0")
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC,
                localized(R.string.biometriccompat_behavior_help_not_registered)
            )
            return
        }

        val threshold = thresholdFor(sample.mode)
        e(
            "BehaviorBiometricManager.authenticate templates=$templateCount " +
                "score=${bestMatch.score} threshold=$threshold metrics=$metrics"
        )
        if (bestMatch.score >= threshold) {
            session.runIfActive { resetTemporaryLockoutState(prefs) } ?: return
            finishWithSuccess(
                callback,
                crypto,
                session,
                resultHandler,
                localized(R.string.biometriccompat_behavior_help_accepted)
            )
        } else {
            val updatedLockout = session.runIfActive {
                recordFailedAttempt(prefs, LOCKOUT_POLICY)
                getLockoutError()
            }
            if (!session.isActive) return
            if (updatedLockout != null) {
                finishWithError(callback, updatedLockout, lockoutMessage(updatedLockout))
            } else {
                finishWithError(
                    callback,
                    CUSTOM_BIOMETRIC_ERROR_NOT_MATCHED,
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
        callback: AuthenticationCallback,
        crypto: CryptoObject?,
        session: SoftwareBiometricWorkSession,
        resultHandler: Handler,
        helpMessage: CharSequence
    ) {
        resultHandler.postDelayed({
            if (!session.isActive) return@postDelayed
            if (BiometricPromptCompat.hasDetectedRuntimeHooks()) {
                finishWithError(callback, CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                    localized(R.string.biometriccompat_behavior_help_unavailable))
            } else {
                callback.onAuthenticationHelp(CUSTOM_BIOMETRIC_ACQUIRED_GOOD, helpMessage)
                callback.onAuthenticationSucceeded(AuthenticationResult(crypto))
            }
        }, RESULT_DELAY_MS)
    }

    private fun finishWithError(
        callback: AuthenticationCallback,
        error: Int,
        message: CharSequence
    ) {
        callback.onAuthenticationError(error, message)
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
        val WORKER = newSoftwareBiometricWorker("BehaviorBiometricManager")
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
