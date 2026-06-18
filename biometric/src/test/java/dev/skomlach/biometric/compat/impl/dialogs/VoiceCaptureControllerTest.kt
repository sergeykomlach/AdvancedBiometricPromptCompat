package dev.skomlach.biometric.compat.impl.dialogs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCaptureControllerTest {
    @Test
    fun resolveVoicePhraseKeepsTextIndependentFlowWhenPhraseIsMissing() {
        assertNull(resolveVoicePhrase(null, null))
        assertNull(resolveVoicePhrase("  ", "  "))
    }

    @Test
    fun resolveVoicePhraseUsesBuilderPhraseBeforeExtrasPhrase() {
        assertEquals("bonjour", resolveVoicePhrase(" bonjour ", "hello"))
    }

    @Test
    fun resolveVoicePhraseFallsBackToExtrasPhrase() {
        assertEquals("hola", resolveVoicePhrase(null, " hola "))
    }
}
