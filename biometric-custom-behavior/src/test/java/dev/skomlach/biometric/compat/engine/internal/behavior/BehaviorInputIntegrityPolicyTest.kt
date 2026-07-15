package dev.skomlach.biometric.compat.engine.internal.behavior

import org.junit.Assert.assertEquals
import org.junit.Test

class BehaviorInputIntegrityPolicyTest {
    @Test
    fun acceptsMonotonicTypingEventsWithinActiveWindow() {
        assertEquals(
            BehaviorInputIntegrityDecision.ACCEPT,
            evaluateTypingIntegrity(
                downs = listOf(1_000L, 1_120L, 1_260L),
                ups = listOf(1_080L, 1_190L, 1_330L),
                phraseLength = 3,
                startedAtMs = 900L,
                nowMs = 1_400L,
                maxInterEventGapMs = 500L
            )
        )
    }

    @Test
    fun rejectsNegativeDwellAndNonMonotonicEvents() {
        assertEquals(
            BehaviorInputIntegrityDecision.NEGATIVE_DWELL,
            evaluateTypingIntegrity(
                downs = listOf(1_000L, 1_120L),
                ups = listOf(900L, 1_190L),
                phraseLength = 2,
                startedAtMs = 900L,
                nowMs = 1_300L,
                maxInterEventGapMs = 500L
            )
        )
        assertEquals(
            BehaviorInputIntegrityDecision.NON_MONOTONIC_TIMESTAMPS,
            evaluateTypingIntegrity(
                downs = listOf(1_000L, 900L),
                ups = listOf(1_080L, 1_190L),
                phraseLength = 2,
                startedAtMs = 800L,
                nowMs = 1_300L,
                maxInterEventGapMs = 500L
            )
        )
    }

    @Test
    fun rejectsStaleTypingAndCancelledSignature() {
        assertEquals(
            BehaviorInputIntegrityDecision.STALE_CAPTURE,
            evaluateTypingIntegrity(
                downs = listOf(1_000L, 1_120L),
                ups = listOf(1_080L, 1_190L),
                phraseLength = 2,
                startedAtMs = 900L,
                nowMs = 2_000L,
                maxInterEventGapMs = 500L
            )
        )
        assertEquals(
            BehaviorInputIntegrityDecision.CANCELLED_CAPTURE,
            evaluateSignatureIntegrity(
                points = listOf(BehaviorPoint(0f, 0f, 1_000L)),
                startedAtMs = 900L,
                nowMs = 1_100L,
                cancelled = true
            )
        )
    }

    @Test
    fun rejectsInvalidSignaturePoint() {
        assertEquals(
            BehaviorInputIntegrityDecision.INVALID_POINT,
            evaluateSignatureIntegrity(
                points = listOf(BehaviorPoint(Float.NaN, 0f, 1_000L)),
                startedAtMs = 900L,
                nowMs = 1_100L,
                cancelled = false
            )
        )
    }
}
