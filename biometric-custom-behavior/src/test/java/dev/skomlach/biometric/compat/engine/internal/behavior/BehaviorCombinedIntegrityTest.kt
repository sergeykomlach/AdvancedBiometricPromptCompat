package dev.skomlach.biometric.compat.engine.internal.behavior

import org.junit.Assert.assertEquals
import org.junit.Test

class BehaviorCombinedIntegrityTest {
    @Test fun drawingAfterTypingKeepsCombinedCaptureFresh() {
        assertEquals(BehaviorInputIntegrityDecision.ACCEPT, evaluate(sample(4_500L)))
    }

    @Test fun typingOnlyStillRejectsOldInput() {
        assertEquals(BehaviorInputIntegrityDecision.STALE_CAPTURE,
            evaluate(sample(4_500L).copy(mode = BehaviorMode.TYPING)))
    }

    @Test fun bothChannelsGoingIdleStillRejectsCombinedInput() {
        assertEquals(BehaviorInputIntegrityDecision.STALE_CAPTURE, evaluate(sample(2_000L)))
    }

    @Test fun futureSignatureCannotFreshenOldTyping() {
        assertEquals(BehaviorInputIntegrityDecision.INVALID_POINT, evaluate(sample(4_700L)))
    }

    @Test fun futureTypingIsRejectedEvenWithAValidSignature() {
        val future = sample(4_500L).copy(keyUpTimesMs = listOf(80L, 200L, 360L, 550L, 4_700L))
        assertEquals(BehaviorInputIntegrityDecision.NON_MONOTONIC_TIMESTAMPS, evaluate(future))
    }

    @Test fun typingAfterDrawingAlsoKeepsCombinedCaptureFresh() {
        val fresh = sample(1_500L).copy(
            keyDownTimesMs = listOf(3_800L, 3_920L, 4_060L, 4_220L, 4_400L),
            keyUpTimesMs = listOf(3_880L, 4_010L, 4_160L, 4_330L, 4_500L))
        assertEquals(BehaviorInputIntegrityDecision.ACCEPT, evaluate(fresh))
    }

    @Test fun outOfSessionTypingCannotBeFreshenedBySignature() {
        val outside = sample(4_500L).copy(keyDownTimesMs = listOf(-1L, 120L, 260L, 420L, 600L))
        assertEquals(BehaviorInputIntegrityDecision.NON_MONOTONIC_TIMESTAMPS, evaluate(outside))
    }

    private fun evaluate(sample: BehaviorSample) =
        evaluateBehaviorSampleIntegrity(sample, 0L, 4_600L, 2_000L)

    private fun sample(signatureEnd: Long) = BehaviorSample(BehaviorMode.COMBINED, "abcde",
        listOf(0L, 120L, 260L, 420L, 600L), listOf(80L, 200L, 360L, 550L, 800L),
        (0..15).map { index -> BehaviorPoint(index * 5f, (index % 4) * 8f,
            1_000L + (signatureEnd - 1_000L) * index / 15) })
}
