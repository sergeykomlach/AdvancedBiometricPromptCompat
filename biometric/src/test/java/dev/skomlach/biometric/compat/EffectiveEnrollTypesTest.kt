package dev.skomlach.biometric.compat

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveEnrollTypesTest {

    @Test
    fun `filters out types that already have system hardware during enroll`() {
        val effectiveTypes = resolveEffectiveEnrollTypes(
            types = listOf(
                BiometricType.BIOMETRIC_FINGERPRINT,
                BiometricType.BIOMETRIC_FACE
            ),
            hasSystemHardware = { type -> type == BiometricType.BIOMETRIC_FINGERPRINT },
            keepSystemType = { false },
            isActive = { true }
        )

        assertEquals(listOf(BiometricType.BIOMETRIC_FACE), effectiveTypes)
    }

    @Test
    fun `keeps only active non system hardware types during enroll`() {
        val effectiveTypes = resolveEffectiveEnrollTypes(
            types = listOf(
                BiometricType.BIOMETRIC_FINGERPRINT,
                BiometricType.BIOMETRIC_FACE,
                BiometricType.BIOMETRIC_IRIS
            ),
            hasSystemHardware = { type -> type == BiometricType.BIOMETRIC_FINGERPRINT },
            keepSystemType = { false },
            isActive = { type -> type == BiometricType.BIOMETRIC_FACE }
        )

        assertEquals(listOf(BiometricType.BIOMETRIC_FACE), effectiveTypes)
    }

    @Test
    fun `keeps system hardware face during enroll when selected route stays on hardware`() {
        val hardwareRoute = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FACE,
            provider = BiometricProviderType.HARDWARE,
            usesBiometricPromptHardware = true,
            permissions = emptyList()
        )
        val effectiveTypes = resolveEffectiveEnrollTypes(
            types = listOf(
                BiometricType.BIOMETRIC_FACE
            ),
            hasSystemHardware = { type -> type == BiometricType.BIOMETRIC_FACE },
            keepSystemType = { type ->
                type == BiometricType.BIOMETRIC_FACE &&
                        shouldKeepSystemEnrollType(hardwareRoute)
            },
            isActive = { true }
        )

        assertEquals(
            listOf(BiometricType.BIOMETRIC_FACE),
            effectiveTypes
        )
    }
}
