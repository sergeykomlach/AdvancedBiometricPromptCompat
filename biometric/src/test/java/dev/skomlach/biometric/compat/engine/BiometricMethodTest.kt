package dev.skomlach.biometric.compat.engine

import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class BiometricMethodTest {
    @Test fun softwareProviderCanRegisterAgainAfterUnload() {
        for (type in listOf(BiometricType.BIOMETRIC_FACE, BiometricType.BIOMETRIC_VOICE)) {
            val method = if (type == BiometricType.BIOMETRIC_FACE) {
                BiometricMethod.CUSTOM_FACE
            } else {
                BiometricMethod.CUSTOM_VOICE
            }
            val originalId = method.id
            val providerId = Int.MIN_VALUE + type.ordinal
            try {
                assertSame(method, BiometricMethod.createCustomModule(providerId, type))
                repeat(3) {
                    assertSame(method, BiometricMethod.createCustomModule(providerId, type))
                    assertEquals(providerId, method.id)
                }
            } finally {
                BiometricMethod.createCustomModule(originalId, type)
            }
        }
    }

    @Test fun hardwareMethodIdCannotBeClaimedBySoftware() {
        val originalId = BiometricMethod.CUSTOM_FACE.id
        try {
            BiometricMethod.createCustomModule(BiometricMethod.FACE_ANDROIDAPI.id, BiometricType.BIOMETRIC_FACE)
            fail("Hardware id collision must be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals(originalId, BiometricMethod.CUSTOM_FACE.id)
        }
    }

    @Test fun differentSoftwareTypesCannotShareAnId() {
        val originalId = BiometricMethod.CUSTOM_VOICE.id
        try {
            BiometricMethod.createCustomModule(BiometricMethod.CUSTOM_FACE.id, BiometricType.BIOMETRIC_VOICE)
            fail("Cross-provider id collision must be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals(originalId, BiometricMethod.CUSTOM_VOICE.id)
        }
    }
}
