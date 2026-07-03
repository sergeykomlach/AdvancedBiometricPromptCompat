package dev.skomlach.biometric.compat

import org.junit.Assert.assertEquals
import org.junit.Test

class EnrollOutcomeResolverTest {

    @Test
    fun `pre satisfied enroll results keep already enrolled types outside pending scope`() {
        val results = resolvePreSatisfiedEnrollResults(
            scopeTypes = listOf(
                BiometricType.BIOMETRIC_FACE,
                BiometricType.BIOMETRIC_VOICE
            ),
            pendingTypes = listOf(BiometricType.BIOMETRIC_VOICE),
            isEnrolled = { type -> type == BiometricType.BIOMETRIC_FACE }
        )

        assertEquals(
            setOf(AuthenticationResult(BiometricType.BIOMETRIC_FACE)),
            results
        )
    }

    @Test
    fun `terminal enroll succeeds for any when hardware already enrolled and software fails`() {
        val outcome = resolveEnrollTerminalOutcome(
            confirmation = BiometricConfirmation.ANY,
            scopeTypes = listOf(
                BiometricType.BIOMETRIC_FACE,
                BiometricType.BIOMETRIC_VOICE
            ),
            successResults = setOf(AuthenticationResult(BiometricType.BIOMETRIC_FACE)),
            failureResults = setOf(
                AuthenticationResult(
                    BiometricType.BIOMETRIC_VOICE,
                    reason = AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR
                )
            ),
            terminal = true
        )

        assertEquals(EnrollTerminalStatus.SUCCEEDED, outcome.status)
        assertEquals(
            setOf(AuthenticationResult(BiometricType.BIOMETRIC_FACE)),
            outcome.results
        )
    }

    @Test
    fun `terminal enroll fails for all when one pending biometric fails`() {
        val outcome = resolveEnrollTerminalOutcome(
            confirmation = BiometricConfirmation.ALL,
            scopeTypes = listOf(
                BiometricType.BIOMETRIC_FACE,
                BiometricType.BIOMETRIC_VOICE
            ),
            successResults = setOf(AuthenticationResult(BiometricType.BIOMETRIC_FACE)),
            failureResults = setOf(
                AuthenticationResult(
                    BiometricType.BIOMETRIC_VOICE,
                    reason = AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR
                )
            ),
            terminal = true
        )

        assertEquals(EnrollTerminalStatus.FAILED, outcome.status)
        assertEquals(
            setOf(
                AuthenticationResult(
                    BiometricType.BIOMETRIC_VOICE,
                    reason = AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR
                )
            ),
            outcome.results
        )
    }

    @Test
    fun `terminal enroll succeeds immediately when only already enrolled hardware is in scope`() {
        val preSatisfied = resolvePreSatisfiedEnrollResults(
            scopeTypes = listOf(BiometricType.BIOMETRIC_FACE),
            pendingTypes = emptyList(),
            isEnrolled = { true }
        )

        val outcome = resolveEnrollTerminalOutcome(
            confirmation = BiometricConfirmation.ALL,
            scopeTypes = listOf(BiometricType.BIOMETRIC_FACE),
            successResults = preSatisfied,
            terminal = true
        )

        assertEquals(EnrollTerminalStatus.SUCCEEDED, outcome.status)
        assertEquals(
            setOf(AuthenticationResult(BiometricType.BIOMETRIC_FACE)),
            outcome.results
        )
    }
}
