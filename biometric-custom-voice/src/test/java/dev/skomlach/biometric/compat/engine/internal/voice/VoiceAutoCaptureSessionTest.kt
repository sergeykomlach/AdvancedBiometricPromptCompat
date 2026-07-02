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
            callback = callback
        )

        session.start()
        session.onSampleCaptured(validSample(seed = 1))
        session.onSampleCaptured(validSample(seed = 2))

        assertFalse(session.isReadyToStartAuth())
        assertEquals(2, session.collectedSampleCount())
        assertTrue(callback.messages.first().contains("1"))

        session.onSampleCaptured(validSample(seed = 3))

        assertTrue(session.isReadyToStartAuth())
        assertEquals(3, session.collectedSampleCount())
        assertTrue(hasVoiceInput(session.preparedExtras()))
        assertEquals("open sesame", session.preparedExtras()?.getString(VOICE_EXTRA_PHRASE))
    }

    @Test
    fun authSessionBecomesReadyAfterOneSample() {
        val session = VoiceAutoCaptureSession(
            enroll = false,
            existingExtras = Bundle(),
            phrase = null,
            callback = RecordingCallback()
        )

        session.start()
        session.onSampleCaptured(validSample(seed = 7))

        assertTrue(session.isReadyToStartAuth())
        assertEquals(1, session.collectedSampleCount())
        assertTrue(hasVoiceInput(session.preparedExtras()))
    }

    private fun validSample(seed: Int): FloatArray {
        return FloatArray(16_000) { index ->
            (0.21f * kotlin.math.sin((index + seed) / 9.0)).toFloat()
        }
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
