package dev.skomlach.biometric.compat.engine.internal.behavior

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.biometric.custom.behavior.R
import dev.skomlach.common.translate.LocalizationHelper
import java.util.concurrent.atomic.AtomicBoolean

class BehaviorBiometricManager(
    private val context: Context,
    private val store: BehaviorTemplateStore = BehaviorTemplateStore()
) : AbstractSoftwareBiometricManager() {

    override val biometricType: BiometricType = BiometricType.BIOMETRIC_BEHAVIOR
    override val priority: Int = PRIORITY_BELOW_SYSTEM_HARDWARE

    private val sessionActive = AtomicBoolean(false)
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

    override fun authenticate(
        crypto: CryptoObject?,
        flags: Int,
        cancel: CancellationSignal?,
        callback: AuthenticationCallback?,
        handler: Handler?,
        extra: Bundle?
    ) {
        cancelActiveSession()
        sessionActive.set(true)
        currentHandler = handler ?: Handler(Looper.getMainLooper())
        cancel?.setOnCancelListener {
            cancelActiveSession()
            callback?.onAuthenticationCancelled()
        }

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

        val sample = BehaviorSample.fromBundle(extra)
        if (sample == null) {
            e("BehaviorBiometricManager.authenticate sample=missing_or_incomplete")
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                localized(R.string.biometriccompat_behavior_help_sample_missing)
            )
            return
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

        if (extra?.getBoolean(IS_ENROLLMENT_KEY, false) == true) {
            val tag = store.save(extra.getString(ENROLLMENT_TAG_KEY), sample)
            resetTemporaryLockoutState(prefs)
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
            resetTemporaryLockoutState(prefs)
            finishWithSuccess(
                callback,
                crypto,
                localized(R.string.biometriccompat_behavior_help_accepted)
            )
        } else {
            recordFailedAttempt(prefs, LOCKOUT_POLICY)
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
        resultRunnable = Runnable {
            if (sessionActive.compareAndSet(true, false)) {
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
        if (sessionActive.compareAndSet(true, false)) {
            callback?.onAuthenticationError(error, message)
        }
    }

    private fun cancelActiveSession() {
        sessionActive.set(false)
        resultRunnable?.let { currentHandler.removeCallbacks(it) }
        resultRunnable = null
    }

    private fun lockoutMessage(error: Int): CharSequence {
        return when (error) {
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
