package dev.skomlach.biometric.compat.engine.internal.voice

import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VoiceProviderTest {
    @Test
    fun voiceProviderExposesVoicePromptFactory() {
        val factory = VoiceProvider().getPromptFactory()

        assertNotNull(factory)
        assertEquals(BiometricType.BIOMETRIC_VOICE, factory.biometricType)
    }
}
