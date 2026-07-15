package dev.skomlach.biometric.compat.engine.internal.voice

internal enum class VoiceReplayDecision {
    ACCEPT,
    REJECT_REPLAY
}

internal fun fingerprintVoiceSample(sample: VoiceSample): Long? {
    val pcm = sample.pcmFloat ?: return null
    var hash = FNV_OFFSET_BASIS
    hash = hash xor sample.sampleRateHz.toLong()
    hash *= FNV_PRIME
    hash = hash xor pcm.size.toLong()
    hash *= FNV_PRIME
    pcm.forEach { value ->
        hash = hash xor value.toRawBits().toLong()
        hash *= FNV_PRIME
    }
    return hash
}

internal fun evaluateVoiceReplay(
    previousFingerprint: Long?,
    currentFingerprint: Long,
    nowMs: Long,
    previousAtMs: Long,
    freshnessWindowMs: Long
): VoiceReplayDecision {
    if (previousFingerprint == null || freshnessWindowMs <= 0L) {
        return VoiceReplayDecision.ACCEPT
    }
    return if (previousFingerprint == currentFingerprint &&
        nowMs - previousAtMs in 0 until freshnessWindowMs
    ) {
        VoiceReplayDecision.REJECT_REPLAY
    } else {
        VoiceReplayDecision.ACCEPT
    }
}

private const val FNV_OFFSET_BASIS = -3750763034362895579L
private const val FNV_PRIME = 1099511628211L
