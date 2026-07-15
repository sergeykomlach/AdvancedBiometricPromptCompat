package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSampleTrustPolicyTest {
    @Test
    fun authenticationRejectsPrecomputedEmbeddingWithoutPcm() {
        val sample = VoiceSample(
            sampleRateHz = 16_000,
            pcmFloat = null,
            embedding = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            phrase = "challenge"
        )

        assertEquals(
            VoiceSampleTrustDecision.REJECT_PRECOMPUTED_EMBEDDING,
            evaluateVoiceSampleTrust(sample, allowPrecomputedEmbedding = false)
        )
    }

    @Test
    fun capturedPcmRemainsAcceptedByTrustPolicy() {
        val sample = VoiceSample(
            sampleRateHz = 16_000,
            pcmFloat = FloatArray(16_000) { 0.1f },
            embedding = null,
            phrase = "challenge"
        )

        assertEquals(
            VoiceSampleTrustDecision.ACCEPT,
            evaluateVoiceSampleTrust(sample, allowPrecomputedEmbedding = false)
        )
    }

    @Test
    fun precomputedEmbeddingIsRejectedEvenWhenPcmIsAlsoPresent() {
        val sample = VoiceSample(
            sampleRateHz = 16_000,
            pcmFloat = FloatArray(16_000) { 0.1f },
            embedding = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            phrase = "challenge"
        )

        assertEquals(
            VoiceSampleTrustDecision.REJECT_PRECOMPUTED_EMBEDDING,
            evaluateVoiceSampleTrust(sample, allowPrecomputedEmbedding = false)
        )
    }
}
