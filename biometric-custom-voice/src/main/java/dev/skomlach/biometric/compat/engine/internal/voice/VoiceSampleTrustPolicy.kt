package dev.skomlach.biometric.compat.engine.internal.voice

internal enum class VoiceSampleTrustDecision {
    ACCEPT,
    REJECT_PRECOMPUTED_EMBEDDING,
    REJECT_MISSING_PCM
}

internal fun evaluateVoiceSampleTrust(
    sample: VoiceSample,
    allowPrecomputedEmbedding: Boolean
): VoiceSampleTrustDecision {
    if (sample.embedding != null && !allowPrecomputedEmbedding) {
        return VoiceSampleTrustDecision.REJECT_PRECOMPUTED_EMBEDDING
    }
    if (sample.pcmFloat == null && sample.embedding == null) {
        return VoiceSampleTrustDecision.REJECT_MISSING_PCM
    }
    return VoiceSampleTrustDecision.ACCEPT
}
