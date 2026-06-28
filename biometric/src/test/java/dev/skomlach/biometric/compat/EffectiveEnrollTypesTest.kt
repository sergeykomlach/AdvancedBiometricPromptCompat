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
            isActive = { type -> type == BiometricType.BIOMETRIC_FACE }
        )

        assertEquals(listOf(BiometricType.BIOMETRIC_FACE), effectiveTypes)
    }
}
