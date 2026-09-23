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
                    assertEquals(originalId, method.id)
                }
            } finally {
                BiometricMethod.createCustomModule(originalId, type)
            }
        }
    }

    @Test fun registeringProvidersNeverMutatesSharedModalityIds() {
        for (type in BiometricType.entries) {
            val before = BiometricMethod.entries.associateWith { it.id }
            BiometricMethod.createCustomModule(-20000 - type.ordinal, type)
            BiometricMethod.createCustomModule(-30000 - type.ordinal, type)
            assertEquals(before, BiometricMethod.entries.associateWith { it.id })
        }
    }

    @Test fun sameTypeProvidersHaveIndependentRegistryAndStorageKeys() {
        for (type in BiometricType.entries) {
            val first = BiometricModuleKey.software(-20000 - type.ordinal, type)
            val second = BiometricModuleKey.software(-30000 - type.ordinal, type)
            val registry = linkedMapOf(first to "preferred", second to "fallback")
            assertEquals(2, registry.size)
            assertEquals("preferred", registry[first])
            assertEquals("fallback", registry[second])
            assertEquals(-20000 - type.ordinal, first.id)
            assertEquals(type, first.biometricType)
            // Recreating a provider must address its original entry after reload.
            assertEquals(first, BiometricModuleKey.software(first.id, type))
            registry.remove(first)
            assertEquals("fallback", registry[second])
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
