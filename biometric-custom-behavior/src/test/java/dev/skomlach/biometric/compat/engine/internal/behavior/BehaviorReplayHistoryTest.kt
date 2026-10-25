package dev.skomlach.biometric.compat.engine.internal.behavior

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorReplayHistoryTest {
    @Test fun unusedSignatureChannelCannotHideTypingReplay() {
        val history = BehaviorReplayHistory()
        val original = sample()
        val replay = original.copy(strokePoints = listOf(BehaviorPoint(20f, 50f, 20L)))
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(original, 1_000L, false))
        assertEquals(BehaviorReplayDecision.REJECT_REPLAY, history.checkAndRemember(replay, 1_100L, false))
    }

    @Test fun unusedTypingChannelCannotHideSignatureReplay() {
        val history = BehaviorReplayHistory()
        val original = BehaviorSample(BehaviorMode.SIGNATURE, null, emptyList(), emptyList(),
            (0 until 24).map { BehaviorPoint(it * 5f, (it % 5) * 12f, it * 15L) })
        val replay = original.copy(phrase = "irrelevant", keyDownTimesMs = listOf(10L), keyUpTimesMs = listOf(50L))
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(original, 1_000L, false))
        assertEquals(BehaviorReplayDecision.REJECT_REPLAY, history.checkAndRemember(replay, 1_100L, false))
    }
    @Test fun anotherStoreInstanceRejectsTheSameAuthenticationPayload() {
        val first = BehaviorTemplateStore { error("Replay admission does not access preferences") }
        val replacement = BehaviorTemplateStore { error("Replay admission does not access preferences") }
        val sample = sample(step = 397L)

        assertEquals(BehaviorReplayDecision.ACCEPT, first.checkAndRememberSample(sample, 100_000L, false))
        assertEquals(BehaviorReplayDecision.REJECT_REPLAY,
            replacement.checkAndRememberSample(sample, 100_001L, false))
    }

    @Test fun interleavingAnotherSampleDoesNotHideReplay() {
        val history = BehaviorReplayHistory()
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(sample(), 1_000L, false))
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(sample(210L), 1_100L, false))
        assertEquals(BehaviorReplayDecision.REJECT_REPLAY, history.checkAndRemember(sample(), 1_200L, false))
    }

    @Test fun differentPhrasesDoNotCauseFalseReplay() {
        val history = BehaviorReplayHistory()
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(sample(), 1_000L, false))
        assertEquals(BehaviorReplayDecision.ACCEPT,
            history.checkAndRemember(sample().copy(phrase = "vwxyz"), 1_100L, false))
    }

    @Test fun shiftedTimestampsDoNotHideReplayAtAdmission() {
        val history = BehaviorReplayHistory()
        val first = sample()
        val shifted = first.copy(keyDownTimesMs = first.keyDownTimesMs.map { it + 50_000L },
            keyUpTimesMs = first.keyUpTimesMs.map { it + 50_000L })
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(first, 1_000L, false))
        assertEquals(BehaviorReplayDecision.REJECT_REPLAY, history.checkAndRemember(shifted, 1_100L, false))
    }

    @Test fun rejectedRetriesDoNotExtendTheFreshnessWindow() {
        val history = BehaviorReplayHistory()
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(sample(), 1_000L, false))
        assertEquals(BehaviorReplayDecision.REJECT_REPLAY, history.checkAndRemember(sample(), 30_999L, false))
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(sample(), 31_000L, false))
    }

    @Test fun enrollmentRemainsRepeatableButCannotBeReplayedForAuthentication() {
        val history = BehaviorReplayHistory()
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(sample(), 1_000L, true))
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(sample(), 1_100L, true))
        assertEquals(BehaviorReplayDecision.REJECT_REPLAY, history.checkAndRemember(sample(), 1_200L, false))
    }

    @Test fun invalidSamplesDoNotPoisonHistory() {
        val history = BehaviorReplayHistory()
        val valid = sample()
        assertEquals(BehaviorReplayDecision.REJECT_INVALID_SAMPLE,
            history.checkAndRemember(valid.copy(phrase = " a   "), 1_000L, false))
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(valid, 1_100L, false))
    }

    @Test fun fullHistoryFailsClosedWithoutEvictingARecentCapture() {
        val history = BehaviorReplayHistory(maxEntries = 2)
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(sample(), 1_000L, false))
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(sample(210L), 1_100L, false))
        assertEquals(BehaviorReplayDecision.REJECT_CAPACITY, history.checkAndRemember(sample(310L), 1_200L, false))
        assertEquals(BehaviorReplayDecision.REJECT_REPLAY, history.checkAndRemember(sample(), 1_300L, false))
        assertEquals(BehaviorReplayDecision.ACCEPT, history.checkAndRemember(sample(310L), 31_000L, false))
    }

    @Test fun simultaneousAdmissionsAcceptExactlyOnce() {
        val history = BehaviorReplayHistory()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map { pool.submit<BehaviorReplayDecision> {
                assertTrue(start.await(5, TimeUnit.SECONDS))
                history.checkAndRemember(sample(), 1_000L, false)
            } }
            start.countDown()
            assertEquals(listOf(BehaviorReplayDecision.ACCEPT, BehaviorReplayDecision.REJECT_REPLAY),
                results.map { it.get(5, TimeUnit.SECONDS) }.sortedBy { it.ordinal })
        } finally {
            pool.shutdownNow()
        }
    }

    private fun sample(step: Long = 150L) = BehaviorSample(BehaviorMode.TYPING, "abcde",
        listOf(0L, step, step * 2 + 20, step * 3 + 50, step * 4 + 90),
        listOf(60L, step + 70, step * 2 + 100, step * 3 + 140, step * 4 + 190), emptyList())
}
