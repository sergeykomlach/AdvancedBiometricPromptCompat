package dev.skomlach.biometric.compat.engine.internal.behavior

import org.junit.Assert.assertEquals
import org.junit.Test

class BehaviorCaptureTimingTest {
    @Test fun priorDeepSleepDoesNotMakeNativeEventsStale() {
        val token = BehaviorCaptureSessionToken(7L, 400_000L, 100_000L)
        val sample = BehaviorSample(BehaviorMode.TYPING, "abc",
            listOf(100_100L, 100_300L, 100_600L).map(token::relativeUptime),
            listOf(100_180L, 100_390L, 100_700L).map(token::relativeUptime), emptyList())

        assertEquals(100L, sample.keyDownTimesMs.first())
        assertEquals(BehaviorInputIntegrityDecision.ACCEPT,
            evaluateBehaviorSampleIntegrity(sample, 0L, token.relativeUptime(101_000L), 2_000L))
    }

    @Test fun signatureEncodingRetainsMillisecondsAfterLongUptime() {
        val token = BehaviorCaptureSessionToken(9L, 2_000_000_001L, 1_000_000_001L)
        val first = token.relativeUptime(1_000_000_114L).toFloat().toLong()
        val second = token.relativeUptime(1_000_000_115L).toFloat().toLong()

        assertEquals(113L, first)
        assertEquals(114L, second)
        assertEquals(BehaviorInputIntegrityDecision.ACCEPT, evaluateSignatureIntegrity(
            listOf(BehaviorPoint(0f, 0f, first), BehaviorPoint(1f, 1f, second)),
            0L, 115L, false))
    }

    @Test fun captureDeadlineStillIncludesSleep() {
        val token = BehaviorCaptureSessionToken(11L, 400_000L, 100_000L)
        assertEquals(1_000L, token.relativeUptime(101_000L))
        assertEquals(BehaviorCaptureSessionDecision.EXPIRED,
            evaluateBehaviorCaptureSession(430_000L, false, token, 11L, 30_000L))
    }
}
