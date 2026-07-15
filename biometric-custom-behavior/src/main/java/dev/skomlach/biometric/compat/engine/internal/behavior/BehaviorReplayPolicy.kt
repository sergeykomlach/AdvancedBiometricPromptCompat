package dev.skomlach.biometric.compat.engine.internal.behavior

internal enum class BehaviorReplayDecision {
    ACCEPT,
    REJECT_REPLAY
}

internal fun fingerprintBehaviorSample(sample: BehaviorSample): Long {
    var hash = FNV_OFFSET_BASIS
    hash = hash.mix(sample.mode.ordinal.toLong())
    hash = hash.mix((sample.phrase?.length ?: 0).toLong())
    sample.keyDownTimesMs.forEach { hash = hash.mix(it) }
    hash = hash.mix(Long.MIN_VALUE)
    sample.keyUpTimesMs.forEach { hash = hash.mix(it) }
    hash = hash.mix(Long.MIN_VALUE)
    sample.strokePoints.forEach { point ->
        hash = hash.mix(point.x.toRawBits().toLong())
        hash = hash.mix(point.y.toRawBits().toLong())
        hash = hash.mix(point.timestampMs)
        hash = hash.mix(point.pressure?.toRawBits()?.toLong() ?: Long.MIN_VALUE)
        hash = hash.mix(point.size?.toRawBits()?.toLong() ?: Long.MIN_VALUE)
        hash = hash.mix(point.strokeId.toLong())
    }
    return hash
}

internal fun evaluateBehaviorReplay(
    previousFingerprint: Long?,
    currentFingerprint: Long,
    nowMs: Long,
    previousAtMs: Long,
    freshnessWindowMs: Long
): BehaviorReplayDecision {
    if (previousFingerprint == null || freshnessWindowMs <= 0L) {
        return BehaviorReplayDecision.ACCEPT
    }
    val elapsed = nowMs - previousAtMs
    return if (previousFingerprint == currentFingerprint && elapsed in 0 until freshnessWindowMs) {
        BehaviorReplayDecision.REJECT_REPLAY
    } else {
        BehaviorReplayDecision.ACCEPT
    }
}

private fun Long.mix(value: Long): Long = (this xor value) * FNV_PRIME

private const val FNV_OFFSET_BASIS = -3750763034362895579L
private const val FNV_PRIME = 1099511628211L
