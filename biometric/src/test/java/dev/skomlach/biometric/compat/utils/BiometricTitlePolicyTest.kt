package dev.skomlach.biometric.compat.utils

import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiometricTitlePolicyTest {

    @Test
    fun `single fingerprint keeps fingerprint-specific prompt`() {
        assertEquals(
            BiometricType.BIOMETRIC_FINGERPRINT,
            modalitySpecificPromptType(setOf(BiometricType.BIOMETRIC_FINGERPRINT))
        )
    }

    @Test
    fun `single face keeps face-specific prompt`() {
        assertEquals(
            BiometricType.BIOMETRIC_FACE,
            modalitySpecificPromptType(setOf(BiometricType.BIOMETRIC_FACE))
        )
    }

    @Test
    fun `single voice keeps voice-specific prompt`() {
        assertEquals(
            BiometricType.BIOMETRIC_VOICE,
            modalitySpecificPromptType(setOf(BiometricType.BIOMETRIC_VOICE))
        )
    }

    @Test
    fun `single behavior keeps behavior-specific prompt`() {
        assertEquals(
            BiometricType.BIOMETRIC_BEHAVIOR,
            modalitySpecificPromptType(setOf(BiometricType.BIOMETRIC_BEHAVIOR))
        )
    }

    @Test
    fun `single palmprint keeps palmprint-specific prompt`() {
        assertEquals(
            BiometricType.BIOMETRIC_PALMPRINT,
            modalitySpecificPromptType(setOf(BiometricType.BIOMETRIC_PALMPRINT))
        )
    }

    @Test
    fun `single heartrate keeps heartrate-specific prompt`() {
        assertEquals(
            BiometricType.BIOMETRIC_HEARTRATE,
            modalitySpecificPromptType(setOf(BiometricType.BIOMETRIC_HEARTRATE))
        )
    }

    @Test
    fun `mixed biometric set falls back to generic prompt`() {
        assertNull(
            modalitySpecificPromptType(
                setOf(
                    BiometricType.BIOMETRIC_FACE,
                    BiometricType.BIOMETRIC_VOICE
                )
            )
        )
    }
}
