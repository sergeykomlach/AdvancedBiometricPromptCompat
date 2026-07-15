package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAttackMatrixTest {
    @Test
    fun knownVoiceAttackVectorsAreRejectedByPolicy() {
        val pcm = FloatArray(128) { it / 100f }
        val embedding = FloatArray(8) { if (it == 0) 1f else 0f }
        val vectors = listOf(
            "embedding-only" to
                (evaluateVoiceSampleTrust(
                    VoiceSample(16_000, null, embedding, null), false
                ) == VoiceSampleTrustDecision.REJECT_PRECOMPUTED_EMBEDDING),
            "pcm-with-injected-embedding" to
                (evaluateVoiceSampleTrust(
                    VoiceSample(16_000, pcm, embedding, null), false
                ) == VoiceSampleTrustDecision.REJECT_PRECOMPUTED_EMBEDDING),
            "missing-phrase" to
                (evaluateVoicePhraseChallenge("challenge", null) ==
                    VoicePhraseChallengeDecision.REJECT_MISSING_PHRASE),
            "recent-replay" to
                (evaluateVoiceReplay(42L, 42L, 10_000L, 8_000L, 5_000L) ==
                    VoiceReplayDecision.REJECT_REPLAY)
        )

        assertEquals(emptyList<String>(), vectors.filterNot { it.second }.map { it.first })
    }
}
