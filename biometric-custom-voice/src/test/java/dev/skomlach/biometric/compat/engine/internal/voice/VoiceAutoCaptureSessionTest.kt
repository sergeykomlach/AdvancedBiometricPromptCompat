package dev.skomlach.biometric.compat.engine.internal.voice

import android.os.Bundle
import dev.skomlach.biometric.compat.AuthenticationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAutoCaptureSessionTest {
    @Test
    fun enrollSessionWaitsForThreeSamplesBeforeReady() {
        val callback = RecordingCallback()
        val session = VoiceAutoCaptureSession(
            enroll = true,
            existingExtras = null,
            phrase = "open sesame",
            callback = callback,
            promptMessageResolver = ::renderPrompt
        )

        session.start()
        session.onCaptureOutcome(
            VoiceCaptureOutcome.Accepted(
                sample = validSample(seed = 1),
                hadSpeechActivity = true
            ),
            nowMs = 1000L
        )
        session.onCaptureOutcome(
            VoiceCaptureOutcome.Accepted(
                sample = validSample(seed = 2),
                hadSpeechActivity = true
            ),
            nowMs = 2000L
        )

        assertFalse(session.isReadyToStartAuth())
        assertEquals(2, session.collectedSampleCount())
        assertEquals(
            listOf(
                "ENROLL:1/3",
                "SPEECH_DETECTED",
                "PROCESSING",
                "ENROLL:2/3",
                "SPEECH_DETECTED",
                "PROCESSING",
                "ENROLL:3/3"
            ),
            callback.messages
        )

        session.onCaptureOutcome(
            VoiceCaptureOutcome.Accepted(
                sample = validSample(seed = 3),
                hadSpeechActivity = true
            ),
            nowMs = 3000L
        )

        assertTrue(session.isReadyToStartAuth())
        assertEquals(3, session.collectedSampleCount())
        assertTrue(hasVoiceInput(session.preparedExtras()))
        assertEquals("open sesame", session.preparedExtras()?.getString(VOICE_EXTRA_PHRASE))
        assertEquals(
            listOf(
                "ENROLL:1/3",
                "SPEECH_DETECTED",
                "PROCESSING",
                "ENROLL:2/3",
                "SPEECH_DETECTED",
                "PROCESSING",
                "ENROLL:3/3",
                "SPEECH_DETECTED",
                "PROCESSING",
                "MATCHING"
            ),
            callback.messages
        )
    }

    @Test
    fun authSessionBecomesReadyAfterOneSample() {
        val callback = RecordingCallback()
        val session = VoiceAutoCaptureSession(
            enroll = false,
            existingExtras = Bundle(),
            phrase = null,
            callback = callback,
            promptMessageResolver = ::renderPrompt
        )

        session.start()
        session.onCaptureOutcome(
            VoiceCaptureOutcome.Accepted(
                sample = validSample(seed = 7),
                hadSpeechActivity = true
            ),
            nowMs = 1000L
        )

        assertTrue(session.isReadyToStartAuth())
        assertEquals(1, session.collectedSampleCount())
        assertTrue(hasVoiceInput(session.preparedExtras()))
        assertEquals(
            listOf("AUTH", "SPEECH_DETECTED", "PROCESSING", "MATCHING"),
            callback.messages
        )
    }

    @Test
    fun recoverableErrorRepeatsCurrentSampleHelp() {
        val callback = RecordingCallback()
        val session = VoiceAutoCaptureSession(
            enroll = true,
            existingExtras = null,
            phrase = null,
            callback = callback,
            promptMessageResolver = ::renderPrompt
        )

        session.start()
        session.onCaptureOutcome(
            VoiceCaptureOutcome.Rejected(
                decision = VoiceCaptureDecision(
                    acceptedSample = null,
                    rejectReason = VoiceCaptureRejectReason.NO_SPEECH,
                    qualityIssue = VoiceQualityIssue.SAMPLE_MISSING,
                    shouldNotifySpeechDetected = false,
                    hadSpeechActivity = false
                )
            ),
            nowMs = 1000L
        )

        assertEquals(
            listOf("ENROLL:1/3", "ENROLL:1/3\nNO_SPEECH"),
            callback.messages
        )
    }

    @Test
    fun acceptedEnrollCaptureAdvancesToNextInstructionState() {
        val callback = RecordingCallback()
        val session = VoiceAutoCaptureSession(
            enroll = true,
            existingExtras = null,
            phrase = "open sesame",
            callback = callback,
            sampleRateHz = 16_000,
            promptMessageResolver = ::renderPrompt
        )

        session.start()
        session.onCaptureOutcome(
            VoiceCaptureOutcome.Accepted(
                sample = FloatArray(16_000),
                hadSpeechActivity = true
            ),
            nowMs = 1000L
        )

        assertEquals(1, session.collectedSampleCount())
        assertEquals(
            VoicePromptState.EnrollInstruction(step = 2, total = 3, retryReason = null),
            callback.lastState
        )
    }

    @Test
    fun sessionExposesOverallInactivityTimeoutSeparatelyFromCaptureWindow() {
        val callback = RecordingCallback()
        val session = VoiceAutoCaptureSession(
            enroll = false,
            existingExtras = null,
            phrase = null,
            callback = callback,
            promptMessageResolver = ::renderPrompt
        )

        session.start(nowMs = 1_000L)

        assertFalse(session.shouldEmitTimeoutOutcome(nowMs = 30_000L))
        assertTrue(session.shouldEmitTimeoutOutcome(nowMs = 31_001L))
    }

    private fun validSample(seed: Int): FloatArray {
        return FloatArray(16_000) { index ->
            (0.21f * kotlin.math.sin((index + seed) / 9.0)).toFloat()
        }
    }

    private fun renderPrompt(state: VoicePromptState, phrase: CharSequence?): VoicePromptRender {
        return when (state) {
            is VoicePromptState.EnrollInstruction -> VoicePromptRender(
                primaryMessage = "ENROLL:${state.step}/${state.total}",
                secondaryMessage = state.retryReason?.name
            )

            is VoicePromptState.AuthInstruction -> VoicePromptRender(
                primaryMessage = "AUTH",
                secondaryMessage = state.retryReason?.name
            )

            VoicePromptState.Listening -> VoicePromptRender("LISTENING")
            VoicePromptState.SpeechDetected -> VoicePromptRender("SPEECH_DETECTED")
            VoicePromptState.ProcessingCapture -> VoicePromptRender("PROCESSING")
            VoicePromptState.Matching -> VoicePromptRender("MATCHING")
            VoicePromptState.Timeout -> VoicePromptRender("TIMEOUT")
            VoicePromptState.Lockout -> VoicePromptRender("LOCKOUT")
        }
    }

    private class RecordingCallback : VoiceAutoCaptureSession.Callback {
        val messages = mutableListOf<String>()
        var lastState: VoicePromptState? = null

        override fun onHelp(message: CharSequence) {
            messages += message.toString()
        }

        override fun onStateChanged(state: VoicePromptState) {
            lastState = state
        }

        override fun onReady(extras: Bundle) = Unit

        override fun onError(result: AuthenticationResult) {
            messages += "error:${result.description}"
        }

        override fun isPromptActive(): Boolean = true
    }
}
