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
            messages = testMessages()
        )

        session.start()
        session.onSpeechDetected()
        session.onSampleCaptured(validSample(seed = 1))
        session.onSpeechDetected()
        session.onSampleCaptured(validSample(seed = 2))

        assertFalse(session.isReadyToStartAuth())
        assertEquals(2, session.collectedSampleCount())
        assertEquals(
            listOf(
                "ENROLL_START:1/3",
                "VOICE_DETECTED",
                "SAVED:1/3",
                "ENROLL_START:2/3",
                "VOICE_DETECTED",
                "SAVED:2/3",
                "ENROLL_START:3/3"
            ),
            callback.messages
        )

        session.onSpeechDetected()
        session.onSampleCaptured(validSample(seed = 3))

        assertTrue(session.isReadyToStartAuth())
        assertEquals(3, session.collectedSampleCount())
        assertTrue(hasVoiceInput(session.preparedExtras()))
        assertEquals("open sesame", session.preparedExtras()?.getString(VOICE_EXTRA_PHRASE))
        assertEquals(
            listOf(
                "ENROLL_START:1/3",
                "VOICE_DETECTED",
                "SAVED:1/3",
                "ENROLL_START:2/3",
                "VOICE_DETECTED",
                "SAVED:2/3",
                "ENROLL_START:3/3",
                "VOICE_DETECTED",
                "PROCESSING"
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
            messages = testMessages()
        )

        session.start()
        session.onSpeechDetected()
        session.onSampleCaptured(validSample(seed = 7))

        assertTrue(session.isReadyToStartAuth())
        assertEquals(1, session.collectedSampleCount())
        assertTrue(hasVoiceInput(session.preparedExtras()))
        assertEquals(
            listOf("AUTH_START", "VOICE_DETECTED", "PROCESSING"),
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
            messages = testMessages()
        )

        session.start()
        session.onSpeechDetected()
        session.onRecoverableError("TRY_AGAIN")

        assertEquals(
            listOf("ENROLL_START:1/3", "VOICE_DETECTED", "TRY_AGAIN", "ENROLL_START:1/3"),
            callback.messages
        )
    }

    private fun validSample(seed: Int): FloatArray {
        return FloatArray(16_000) { index ->
            (0.21f * kotlin.math.sin((index + seed) / 9.0)).toFloat()
        }
    }

    private fun testMessages(): VoiceAutoCaptureSession.Messages {
        return VoiceAutoCaptureSession.Messages(
            authStart = "AUTH_START",
            voiceDetected = "VOICE_DETECTED",
            processing = "PROCESSING",
            enrollRecordingStarted = { current: Int, total: Int -> "ENROLL_START:$current/$total" },
            sampleSavedTemplate = { current: Int, total: Int -> "SAVED:$current/$total" }
        )
    }

    private class RecordingCallback : VoiceAutoCaptureSession.Callback {
        val messages = mutableListOf<String>()

        override fun onHelp(message: CharSequence) {
            messages += message.toString()
        }

        override fun onReady(extras: Bundle) = Unit

        override fun onError(result: AuthenticationResult) {
            messages += "error:${result.description}"
        }

        override fun isPromptActive(): Boolean = true
    }
}
