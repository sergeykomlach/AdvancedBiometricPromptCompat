package dev.skomlach.biometric.compat.engine.internal.behavior

import org.junit.Assert.assertEquals
import org.junit.Test

class BehaviorAttackMatrixTest {
    @Test
    fun rejectsMalformedAndStaleTypingVectors() {
        assertEquals(
            BehaviorInputIntegrityDecision.NEGATIVE_DWELL,
            evaluateTypingIntegrity(
                downs = listOf(1_000L, 1_100L),
                ups = listOf(900L, 1_180L),
                phraseLength = 2,
                startedAtMs = 800L,
                nowMs = 1_200L,
                maxInterEventGapMs = 2_000L
            )
        )
        assertEquals(
            BehaviorInputIntegrityDecision.STALE_CAPTURE,
            evaluateTypingIntegrity(
                downs = listOf(1_000L, 1_100L),
                ups = listOf(1_050L, 1_180L),
                phraseLength = 2,
                startedAtMs = 800L,
                nowMs = 4_000L,
                maxInterEventGapMs = 2_000L
            )
        )
    }

    @Test
    fun rejectsInvalidTouchAndAccessibilityAutomationVectors() {
        assertEquals(
            BehaviorInputIntegrityDecision.INVALID_POINT,
            evaluateSignatureIntegrity(
                points = listOf(BehaviorPoint(100_001f, 0f, 1_000L)),
                startedAtMs = 800L,
                nowMs = 1_200L,
                cancelled = false
            )
        )
        assertEquals(
            BehaviorAccessibilityDecision.REJECT_UNTRUSTED_SERVICE,
            evaluateBehaviorAccessibility(true, false, true)
        )
    }

    @Test
    fun strictSessionTokenCannotBeConsumedTwice() {
        val token = BehaviorCaptureSessionRegistry.start(1_000L)

        assertEquals(
            BehaviorCaptureSessionDecision.ACTIVE,
            BehaviorCaptureSessionRegistry.consume(token.nonce, 1_500L, 30_000L)
        )
        assertEquals(
            BehaviorCaptureSessionDecision.INVALID_TOKEN,
            BehaviorCaptureSessionRegistry.consume(token.nonce, 1_600L, 30_000L)
        )
    }
}
