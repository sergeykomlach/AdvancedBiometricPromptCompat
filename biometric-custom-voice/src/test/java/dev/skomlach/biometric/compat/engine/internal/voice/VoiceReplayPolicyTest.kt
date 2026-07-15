package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VoiceReplayPolicyTest {
    @Test
    fun identicalPcmProducesSameFingerprint() {
        val first = VoiceSample(16_000, FloatArray(128) { it / 100f }, null, null)
        val second = VoiceSample(16_000, FloatArray(128) { it / 100f }, null, null)

        assertEquals(fingerprintVoiceSample(first), fingerprintVoiceSample(second))
    }

    @Test
    fun changedPcmProducesDifferentFingerprint() {
        val first = VoiceSample(16_000, FloatArray(128) { it / 100f }, null, null)
        val second = VoiceSample(16_000, FloatArray(128) { (it + 1) / 100f }, null, null)

        assertNotEquals(fingerprintVoiceSample(first), fingerprintVoiceSample(second))
    }

    @Test
    fun sameFingerprintInsideFreshnessWindowIsRejected() {
        assertEquals(
            VoiceReplayDecision.REJECT_REPLAY,
            evaluateVoiceReplay(42L, 42L, 10_000L, 8_000L, 5_000L)
        )
    }

    @Test
    fun sameFingerprintAfterFreshnessWindowIsAccepted() {
        assertEquals(
            VoiceReplayDecision.ACCEPT,
            evaluateVoiceReplay(42L, 42L, 20_000L, 8_000L, 5_000L)
        )
    }
}
