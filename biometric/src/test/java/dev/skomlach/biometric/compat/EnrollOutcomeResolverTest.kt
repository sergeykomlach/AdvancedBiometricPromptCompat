package dev.skomlach.biometric.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `permission filtering alone does not satisfy enroll confirmation`() {
        val outcome = resolveEnrollSessionOutcome(
            confirmation = BiometricConfirmation.ANY,
            scopeTypes = listOf(BiometricType.BIOMETRIC_VOICE),
            successResults = emptySet(),
            confirmedTypes = emptySet(),
            terminal = true
        )

        assertEquals(EnrollTerminalStatus.FAILED, outcome.status)
        assertFalse(outcome.confirmedThisRun)
    }

    @Test
    fun `hardware fallback confirmation satisfies any after software becomes unavailable`() {
        val outcome = resolveEnrollSessionOutcome(
            confirmation = BiometricConfirmation.ANY,
            scopeTypes = listOf(
                BiometricType.BIOMETRIC_VOICE,
                BiometricType.BIOMETRIC_FINGERPRINT
            ),
            successResults = setOf(AuthenticationResult(BiometricType.BIOMETRIC_FINGERPRINT)),
            confirmedTypes = setOf(BiometricType.BIOMETRIC_FINGERPRINT),
            terminal = true
        )

        assertEquals(EnrollTerminalStatus.SUCCEEDED, outcome.status)
        assertTrue(outcome.confirmedThisRun)
        assertFalse(outcome.rollbackSuccessfulEnrolls)
    }

    @Test
    fun `already enrolled hardware alone does not satisfy current enroll session`() {
        val outcome = resolveEnrollSessionOutcome(
            confirmation = BiometricConfirmation.ANY,
            scopeTypes = listOf(BiometricType.BIOMETRIC_FINGERPRINT),
            successResults = setOf(AuthenticationResult(BiometricType.BIOMETRIC_FINGERPRINT)),
            confirmedTypes = emptySet(),
            terminal = true
        )

        assertEquals(EnrollTerminalStatus.FAILED, outcome.status)
        assertFalse(outcome.confirmedThisRun)
    }

    @Test
    fun `all requires rollback when one prior software enroll succeeded and later one fails`() {
        val outcome = resolveEnrollSessionOutcome(
            confirmation = BiometricConfirmation.ALL,
            scopeTypes = listOf(
                BiometricType.BIOMETRIC_VOICE,
                BiometricType.BIOMETRIC_BEHAVIOR
            ),
            successResults = setOf(AuthenticationResult(BiometricType.BIOMETRIC_VOICE)),
            confirmedTypes = setOf(BiometricType.BIOMETRIC_VOICE),
            canceledResults = setOf(
                AuthenticationResult(
                    BiometricType.BIOMETRIC_BEHAVIOR,
                    reason = AuthenticationFailureReason.CANCELED_BY_USER
                )
            ),
            terminal = true
        )

        assertEquals(EnrollTerminalStatus.FAILED, outcome.status)
        assertTrue(outcome.rollbackSuccessfulEnrolls)
    }
}
