package dev.skomlach.biometric.compat.engine.internal.voice

internal data class VoiceOutcomeProgress(
    val suspiciousCaptureAttempts: Int,
    val lastProgressAtMs: Long
)

internal sealed interface VoiceOutcomeDecision {
    data class Retry(val progress: VoiceOutcomeProgress) : VoiceOutcomeDecision
    data class Lockout(val progress: VoiceOutcomeProgress) : VoiceOutcomeDecision
}

internal class VoiceOutcomePolicy(
    private val maxSuspiciousCaptureAttempts: Int = 3,
    private val inactivityTimeoutMs: Long = 30_000L
) {
    fun onCaptureRejected(
        progress: VoiceOutcomeProgress,
        rejection: VoiceCaptureDecision,
        nowMs: Long
    ): VoiceOutcomeDecision {
        val nextProgress = VoiceOutcomeProgress(
            suspiciousCaptureAttempts = if (shouldCountTowardsAutoCaptureLockout(rejection)) {
                progress.suspiciousCaptureAttempts + 1
            } else {
                progress.suspiciousCaptureAttempts
            },
            lastProgressAtMs = if (rejection.hadSpeechActivity) nowMs else progress.lastProgressAtMs
        )
        return if (nextProgress.suspiciousCaptureAttempts >= maxSuspiciousCaptureAttempts) {
            VoiceOutcomeDecision.Lockout(nextProgress)
        } else {
            VoiceOutcomeDecision.Retry(nextProgress)
        }
    }

    fun isTimedOut(lastProgressAtMs: Long, nowMs: Long): Boolean {
        return nowMs - lastProgressAtMs > inactivityTimeoutMs
    }
}

internal fun shouldCountTowardsAutoCaptureLockout(decision: VoiceCaptureDecision): Boolean {
    return when (decision.rejectReason) {
        VoiceCaptureRejectReason.RECORDER_FAILURE -> true
        VoiceCaptureRejectReason.QUALITY_ISSUE -> {
            decision.hadSpeechActivity && decision.qualityIssue in AUTO_CAPTURE_LOCKOUT_QUALITY_ISSUES
        }

        else -> false
    }
}

private val AUTO_CAPTURE_LOCKOUT_QUALITY_ISSUES = setOf(
    VoiceQualityIssue.SAMPLE_TOO_LONG,
    VoiceQualityIssue.SAMPLE_CLIPPED,
    VoiceQualityIssue.SAMPLE_REPLAY_RISK
)
