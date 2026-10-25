package dev.skomlach.biometric.compat.engine.internal.behavior

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BehaviorReplayPolicyTest {
    private val sample = BehaviorSample(
        mode = BehaviorMode.TYPING,
        phrase = "open sesame",
        keyDownTimesMs = listOf(1_000L, 1_120L, 1_260L),
        keyUpTimesMs = listOf(1_080L, 1_190L, 1_330L),
        strokePoints = emptyList()
    )

    @Test
    fun equalSamplesHaveStableFingerprintButChangedTimingDoesNot() {
        assertEquals(fingerprintBehaviorSample(sample), fingerprintBehaviorSample(sample.copy()))
        assertNotEquals(
            fingerprintBehaviorSample(sample),
            fingerprintBehaviorSample(sample.copy(keyDownTimesMs = listOf(1_000L, 1_130L, 1_260L)))
        )
    }

    @Test
    fun recentDuplicateIsRejectedAndOlderDuplicateIsAccepted() {
        val fingerprint = fingerprintBehaviorSample(sample)

        assertEquals(
            BehaviorReplayDecision.REJECT_REPLAY,
            evaluateBehaviorReplay(fingerprint, fingerprint, 4_000L, 1_000L, 5_000L)
        )
        assertEquals(
            BehaviorReplayDecision.ACCEPT,
            evaluateBehaviorReplay(fingerprint, fingerprint, 7_000L, 1_000L, 5_000L)
        )
    }

    @Test fun shiftingTypingCaptureToANewSessionDoesNotHideReplay() {
        val shifted = sample.copy(
            keyDownTimesMs = sample.keyDownTimesMs.map { it + 50_000L },
            keyUpTimesMs = sample.keyUpTimesMs.map { it + 50_000L }
        )
        assertEquals(fingerprintBehaviorSample(sample), fingerprintBehaviorSample(shifted))
    }

    @Test fun shiftingSignatureCaptureToANewSessionDoesNotHideReplay() {
        val signature = sample.copy(mode = BehaviorMode.SIGNATURE, phrase = null,
            keyDownTimesMs = emptyList(), keyUpTimesMs = emptyList(),
            strokePoints = listOf(BehaviorPoint(10f, 20f, 1_000L), BehaviorPoint(30f, 40f, 1_100L)))
        val shifted = signature.copy(strokePoints = signature.strokePoints.map {
            it.copy(timestampMs = it.timestampMs + 50_000L)
        })
        assertEquals(fingerprintBehaviorSample(signature), fingerprintBehaviorSample(shifted))
    }

    @Test fun differentSameLengthPhrasesAreDifferentPayloads() {
        assertNotEquals(fingerprintBehaviorSample(sample),
            fingerprintBehaviorSample(sample.copy(phrase = "other words")))
    }
}
