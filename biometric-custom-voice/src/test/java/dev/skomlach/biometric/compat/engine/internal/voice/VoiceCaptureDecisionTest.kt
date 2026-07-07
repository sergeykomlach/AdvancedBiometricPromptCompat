package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoiceCaptureDecisionTest {
    @Test
    fun decideVoiceCaptureSampleAcceptsCompletedValidSample() {
        val detection = VoiceStreamingDetection(
            detectedSpeech = true,
            isComplete = true,
            completedSample = voiceLikeTone(durationSeconds = 1.5),
            activeSample = null
        )

        val decision = decideVoiceCaptureSample(detection, SAMPLE_RATE)

        assertEquals(VoiceCaptureRejectReason.NONE, decision.rejectReason)
        assertEquals(VoiceQualityIssue.NONE, decision.qualityIssue)
        assertTrue(decision.shouldNotifySpeechDetected)
        assertNotNull(decision.acceptedSample)
        assertTrue(decision.acceptedSample!!.isNotEmpty())
    }

    @Test
    fun decideVoiceCaptureSampleRejectsIncompleteSampleEvenWhenQualityWouldPass() {
        val detection = VoiceStreamingDetection(
            detectedSpeech = true,
            isComplete = false,
            completedSample = null,
            activeSample = voiceLikeTone(durationSeconds = 1.5)
        )

        val decision = decideVoiceCaptureSample(detection, SAMPLE_RATE)

        assertEquals(VoiceCaptureRejectReason.INCOMPLETE_SAMPLE, decision.rejectReason)
        assertEquals(VoiceQualityIssue.NONE, decision.qualityIssue)
        assertFalse(decision.shouldNotifySpeechDetected)
        assertNull(decision.acceptedSample)
    }

    @Test
    fun decideVoiceCaptureSampleRejectsQuietNoiseAsQualityIssue() {
        val detection = VoiceStreamingDetection(
            detectedSpeech = true,
            isComplete = true,
            completedSample = FloatArray(SAMPLE_RATE * 2) { index ->
                (0.009f * sin(index * 0.71)).toFloat()
            },
            activeSample = null
        )

        val decision = decideVoiceCaptureSample(detection, SAMPLE_RATE)

        assertEquals(VoiceCaptureRejectReason.QUALITY_ISSUE, decision.rejectReason)
        assertEquals(VoiceQualityIssue.SAMPLE_TOO_QUIET, decision.qualityIssue)
        assertFalse(decision.shouldNotifySpeechDetected)
        assertNull(decision.acceptedSample)
    }

    @Test
    fun decideVoiceCaptureSampleRejectsMissingSpeechPayload() {
        val detection = VoiceStreamingDetection(
            detectedSpeech = false,
            isComplete = false,
            completedSample = null,
            activeSample = null
        )

        val decision = decideVoiceCaptureSample(detection, SAMPLE_RATE)

        assertEquals(VoiceCaptureRejectReason.NO_SPEECH, decision.rejectReason)
        assertEquals(VoiceQualityIssue.SAMPLE_MISSING, decision.qualityIssue)
        assertFalse(decision.shouldNotifySpeechDetected)
        assertNull(decision.acceptedSample)
    }

    @Test
    fun noSpeechDoesNotCountTowardsAutoCaptureLockout() {
        val decision = VoiceCaptureDecision(
            acceptedSample = null,
            rejectReason = VoiceCaptureRejectReason.NO_SPEECH,
            qualityIssue = VoiceQualityIssue.SAMPLE_MISSING,
            shouldNotifySpeechDetected = false,
            hadSpeechActivity = false
        )

        assertFalse(shouldCountTowardsAutoCaptureLockout(decision))
    }

    @Test
    fun incompleteSampleWithSpeechDoesNotCountTowardsAutoCaptureLockout() {
        val decision = VoiceCaptureDecision(
            acceptedSample = null,
            rejectReason = VoiceCaptureRejectReason.INCOMPLETE_SAMPLE,
            qualityIssue = VoiceQualityIssue.NONE,
            shouldNotifySpeechDetected = false,
            hadSpeechActivity = true
        )

        assertFalse(shouldCountTowardsAutoCaptureLockout(decision))
    }

    @Test
    fun qualityIssueWithoutSpeechDoesNotCountTowardsAutoCaptureLockout() {
        val decision = VoiceCaptureDecision(
            acceptedSample = null,
            rejectReason = VoiceCaptureRejectReason.QUALITY_ISSUE,
            qualityIssue = VoiceQualityIssue.SAMPLE_TOO_QUIET,
            shouldNotifySpeechDetected = false,
            hadSpeechActivity = false
        )

        assertFalse(shouldCountTowardsAutoCaptureLockout(decision))
    }

    @Test
    fun quietQualityIssueWithSpeechDoesNotCountTowardsAutoCaptureLockout() {
        val decision = VoiceCaptureDecision(
            acceptedSample = null,
            rejectReason = VoiceCaptureRejectReason.QUALITY_ISSUE,
            qualityIssue = VoiceQualityIssue.SAMPLE_TOO_QUIET,
            shouldNotifySpeechDetected = false,
            hadSpeechActivity = true
        )

        assertFalse(shouldCountTowardsAutoCaptureLockout(decision))
    }

    @Test
    fun replayRiskQualityIssueCountsTowardsAutoCaptureLockout() {
        val decision = VoiceCaptureDecision(
            acceptedSample = null,
            rejectReason = VoiceCaptureRejectReason.QUALITY_ISSUE,
            qualityIssue = VoiceQualityIssue.SAMPLE_REPLAY_RISK,
            shouldNotifySpeechDetected = false,
            hadSpeechActivity = true
        )

        assertTrue(shouldCountTowardsAutoCaptureLockout(decision))
    }

    private fun voiceLikeTone(durationSeconds: Double): FloatArray {
        val size = (SAMPLE_RATE * durationSeconds).toInt()
        return FloatArray(size) { index ->
            val time = index.toDouble() / SAMPLE_RATE
            val harmonic = 0.32 * sin(2.0 * PI * 180.0 * time) +
                0.16 * sin(2.0 * PI * 360.0 * time) +
                0.07 * sin(2.0 * PI * 540.0 * time)
            (harmonic + 0.01 * sin(index * 0.19)).toFloat().coerceIn(-0.95f, 0.95f)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
