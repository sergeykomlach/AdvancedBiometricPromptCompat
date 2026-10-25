package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BehaviorSealedCaptureTest {
    private fun sample() = BehaviorSample(BehaviorMode.TYPING, "abcde",
        listOf(0L, 120L, 270L, 420L, 610L), listOf(60L, 190L, 345L, 500L, 705L), emptyList())

    @Test fun transportContainsOnlyHandleAndPreservesUnrelatedExtras() {
        val token = BehaviorCaptureSessionRegistry.start(1_000L)
        val extras = sealBehaviorExtras(Bundle().apply { putString("caller", "kept") }, sample(),
            false, token, 1_800L)!!
        assertEquals("kept", extras.getString("caller"))
        assertEquals(token.nonce, extras.getLong(EXTRA_BEHAVIOR_SESSION_NONCE))
        assertNull(BehaviorSample.fromBundle(extras))
        assertFalse(extras.containsKey(BehaviorSample.EXTRA_BEHAVIOR_PHRASE))
        assertFalse(extras.containsKey(BehaviorSample.EXTRA_BEHAVIOR_KEY_DOWNS))
        assertFalse(extras.containsKey(BehaviorSample.EXTRA_BEHAVIOR_POINTS))
        assertEquals(sample(), BehaviorCaptureSessionRegistry.consumeSample(token.nonce, 1_900L, false))
        assertNull(BehaviorCaptureSessionRegistry.consumeSample(token.nonce, 2_000L, false))
    }

    @Test fun enrollmentCaptureCannotBeRelabeledAsAuthentication() {
        val token = BehaviorCaptureSessionRegistry.start(1_000L)
        assertNotNull(sealBehaviorExtras(null, sample(), true, token, 1_800L))
        assertNull(BehaviorCaptureSessionRegistry.consumeSample(token.nonce, 1_900L, false))
        assertNull(BehaviorCaptureSessionRegistry.consumeSample(token.nonce, 2_000L, true))
    }

    @Test fun sealedSampleIsIndependentOfCallerMutableListsAndCannotBeResealed() {
        val downs = sample().keyDownTimesMs.toMutableList()
        val token = BehaviorCaptureSessionRegistry.start(1_000L)
        assertNotNull(sealBehaviorExtras(null, sample().copy(keyDownTimesMs = downs), false, token, 1_800L))
        downs[1] = 999L
        assertNull(sealBehaviorExtras(null, sample().copy(phrase = "other"), false, token, 1_900L))
        assertEquals(sample(), BehaviorCaptureSessionRegistry.consumeSample(token.nonce, 2_000L, false))
    }

    @Test fun expiredOrInvalidatedHandleCannotRecoverItsSample() {
        val token = BehaviorCaptureSessionRegistry.start(1_000L)
        assertNotNull(sealBehaviorExtras(null, sample(), false, token, 1_800L))
        assertNull(BehaviorCaptureSessionRegistry.consumeSample(token.nonce, 31_000L, false))
        assertNull(BehaviorCaptureSessionRegistry.token(token.nonce))
        val next = BehaviorCaptureSessionRegistry.start(40_000L)
        assertNotNull(sealBehaviorExtras(null, sample(), false, next, 40_800L))
        BehaviorCaptureSessionRegistry.invalidate(next)
        assertNull(BehaviorCaptureSessionRegistry.consumeSample(next.nonce, 41_000L, false))
    }

    @Test fun bareUnsubmittedNonceCannotAttestAnySample() {
        val token = BehaviorCaptureSessionRegistry.start(1_000L)
        assertNull(BehaviorCaptureSessionRegistry.consumeSample(token.nonce, 1_800L, false))
    }
}
