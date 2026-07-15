package dev.skomlach.biometric.compat.engine.internal.behavior

import org.junit.Assert.assertEquals
import org.junit.Test

class BehaviorCaptureSessionPolicyTest {
    @Test
    fun activeSessionAcceptsMatchingFreshToken() {
        val token = BehaviorCaptureSessionToken(nonce = 7L, startedAtMs = 1_000L)

        assertEquals(
            BehaviorCaptureSessionDecision.ACTIVE,
            evaluateBehaviorCaptureSession(1_500L, false, token, 7L, 5_000L)
        )
    }

    @Test
    fun expiredSessionIsRejected() {
        val token = BehaviorCaptureSessionToken(nonce = 7L, startedAtMs = 1_000L)

        assertEquals(
            BehaviorCaptureSessionDecision.EXPIRED,
            evaluateBehaviorCaptureSession(6_000L, false, token, 7L, 5_000L)
        )
    }

    @Test
    fun mismatchedTokenAndSecondSubmitAreRejected() {
        val token = BehaviorCaptureSessionToken(nonce = 7L, startedAtMs = 1_000L)

        assertEquals(
            BehaviorCaptureSessionDecision.INVALID_TOKEN,
            evaluateBehaviorCaptureSession(1_500L, false, token, 8L, 5_000L)
        )
        assertEquals(
            BehaviorCaptureSessionDecision.ALREADY_SUBMITTED,
            evaluateBehaviorCaptureSession(1_500L, true, token, 7L, 5_000L)
        )
    }
}
