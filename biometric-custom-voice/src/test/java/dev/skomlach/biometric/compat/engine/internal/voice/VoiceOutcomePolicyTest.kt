package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceOutcomePolicyTest {
    @Test
    fun noSpeechDoesNotIncreaseSuspiciousAttemptCount() {
        val policy = VoiceOutcomePolicy()
        val progress = VoiceOutcomeProgress(suspiciousCaptureAttempts = 0, lastProgressAtMs = 1000L)

        val decision = policy.onCaptureRejected(
            progress = progress,
            rejection = VoiceCaptureDecision(
                acceptedSample = null,
                rejectReason = VoiceCaptureRejectReason.NO_SPEECH,
                qualityIssue = VoiceQualityIssue.SAMPLE_MISSING,
                shouldNotifySpeechDetected = false,
                hadSpeechActivity = false
            ),
            nowMs = 2000L
        )

        assertTrue(decision is VoiceOutcomeDecision.Retry)
        assertEquals(0, (decision as VoiceOutcomeDecision.Retry).progress.suspiciousCaptureAttempts)
    }

    @Test
    fun replayRiskEventuallyLocksOut() {
        val policy = VoiceOutcomePolicy(maxSuspiciousCaptureAttempts = 3, inactivityTimeoutMs = 30_000L)
        var progress = VoiceOutcomeProgress(suspiciousCaptureAttempts = 0, lastProgressAtMs = 1000L)

        repeat(2) { index ->
            val retry = policy.onCaptureRejected(
                progress = progress,
                rejection = VoiceCaptureDecision(
                    acceptedSample = null,
                    rejectReason = VoiceCaptureRejectReason.QUALITY_ISSUE,
                    qualityIssue = VoiceQualityIssue.SAMPLE_REPLAY_RISK,
                    shouldNotifySpeechDetected = false,
                    hadSpeechActivity = true
                ),
                nowMs = 2000L + index
            ) as VoiceOutcomeDecision.Retry
            progress = retry.progress
        }

        val lastDecision = policy.onCaptureRejected(
            progress = progress,
            rejection = VoiceCaptureDecision(
                acceptedSample = null,
                rejectReason = VoiceCaptureRejectReason.QUALITY_ISSUE,
                qualityIssue = VoiceQualityIssue.SAMPLE_REPLAY_RISK,
                shouldNotifySpeechDetected = false,
                hadSpeechActivity = true
            ),
            nowMs = 3000L
        )

        assertTrue(lastDecision is VoiceOutcomeDecision.Lockout)
    }

    @Test
    fun inactivityTimeoutUsesSessionProgressInsteadOfRawAttemptCount() {
        val policy = VoiceOutcomePolicy()

        assertFalse(policy.isTimedOut(lastProgressAtMs = 5_000L, nowMs = 34_000L))
        assertTrue(policy.isTimedOut(lastProgressAtMs = 5_000L, nowMs = 35_001L))
    }
}
