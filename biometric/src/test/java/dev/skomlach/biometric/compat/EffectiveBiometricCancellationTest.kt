package dev.skomlach.biometric.compat

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveBiometricCancellationTest {

    @Test
    fun `returns canceled results for each original type when effective list becomes empty`() {
        val results = emptyEffectiveBiometricCancellationResults(
            listOf(
                BiometricType.BIOMETRIC_FINGERPRINT,
                BiometricType.BIOMETRIC_FACE
            )
        )

        assertEquals(
            setOf(
                AuthenticationResult(
                    BiometricType.BIOMETRIC_FINGERPRINT,
                    reason = AuthenticationFailureReason.CANCELED_BY_USER
                ),
                AuthenticationResult(
                    BiometricType.BIOMETRIC_FACE,
                    reason = AuthenticationFailureReason.CANCELED_BY_USER
                )
            ),
            results
        )
    }

    @Test
    fun `falls back to biometric any when original list is already empty`() {
        val results = emptyEffectiveBiometricCancellationResults(emptyList())

        assertEquals(
            setOf(
                AuthenticationResult(
                    BiometricType.BIOMETRIC_ANY,
                    reason = AuthenticationFailureReason.CANCELED_BY_USER
                )
            ),
            results
        )
    }
}
