package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceChallengeGuardPolicyTest {
    @Test
    fun `active step remains valid before timeout`() {
        assertEquals(
            FaceChallengeGuardDecision.ALLOW,
            evaluateFaceChallengeGuard(
                stepStartedAtMs = 1_000L,
                nowMs = 3_000L,
                rejectedAttempts = 0,
                maxRejectedAttempts = 3,
                stepTimeoutMs = 5_000L
            )
        )
    }

    @Test
    fun `expired step is rejected`() {
        assertEquals(
            FaceChallengeGuardDecision.TIMEOUT,
            evaluateFaceChallengeGuard(1_000L, 6_001L, 0, 3, 5_000L)
        )
    }

    @Test
    fun `too many rejected attempts are bounded`() {
        assertEquals(
            FaceChallengeGuardDecision.ATTEMPTS_EXCEEDED,
            evaluateFaceChallengeGuard(1_000L, 2_000L, 3, 3, 5_000L)
        )
    }
}
