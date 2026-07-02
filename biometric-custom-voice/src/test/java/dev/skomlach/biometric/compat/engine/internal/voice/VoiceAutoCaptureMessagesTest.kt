package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAutoCaptureMessagesTest {
    @Test
    fun enrollProgressMessagesUseProvidedTemplates() {
        val messages = VoiceAutoCaptureSession.Messages(
            authStart = "AUTH_START",
            voiceDetected = "VOICE_DETECTED",
            processing = "PROCESSING",
            enrollRecordingStarted = { current: Int, total: Int -> "START:$current/$total" },
            sampleSavedTemplate = { current: Int, total: Int -> "SAVED:$current/$total" }
        )

        assertEquals("START:2/3", messages.recordingStarted(current = 2, total = 3, enroll = true))
        assertEquals("SAVED:2/3", messages.sampleSaved(current = 2, total = 3))
    }
}
