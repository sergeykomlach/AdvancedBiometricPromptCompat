@file:Suppress("MatchingDeclarationName")

package dev.skomlach.biometric.compat.engine.internal.behavior

internal enum class BehaviorReplayDecision {
    ACCEPT,
    REJECT_REPLAY,
    REJECT_INVALID_SAMPLE,
    REJECT_CAPACITY
}

internal fun fingerprintBehaviorSample(sample: BehaviorSample): Long {
    return fingerprintNormalizedSample(sample.normalizedForMode())
}

private fun fingerprintNormalizedSample(sample: BehaviorSample): Long {
    var hash = FNV_OFFSET_BASIS
    hash = hash.mix(sample.mode.ordinal.toLong())
    hash = hash.mix((sample.phrase?.length ?: 0).toLong())
    sample.phrase?.forEach { hash = hash.mix(it.code.toLong()) }
    val typingStart = sample.keyDownTimesMs.firstOrNull() ?: 0L
    sample.keyDownTimesMs.forEach { hash = hash.mix(it - typingStart) }
    hash = hash.mix(Long.MIN_VALUE)
    sample.keyUpTimesMs.forEach { hash = hash.mix(it - typingStart) }
    hash = hash.mix(Long.MIN_VALUE)
    val signatureStart = sample.strokePoints.firstOrNull()?.timestampMs ?: 0L
    var previousStroke: Int? = null
    var strokeOrdinal = -1L
    sample.strokePoints.forEach { point ->
        if (previousStroke != point.strokeId) {
            previousStroke = point.strokeId
            strokeOrdinal++
        }
        hash = hash.mix(point.x.toRawBits().toLong())
        hash = hash.mix(point.y.toRawBits().toLong())
        hash = hash.mix(point.timestampMs - signatureStart)
        hash = hash.mix(point.pressure?.toRawBits()?.toLong() ?: Long.MIN_VALUE)
        hash = hash.mix(point.size?.toRawBits()?.toLong() ?: Long.MIN_VALUE)
        hash = hash.mix(strokeOrdinal)
    }
    return hash
}

/** Process-local recent captures; never log or persist these fingerprints. */
internal class BehaviorReplayHistory(
    private val freshnessWindowMs: Long = 30_000L,
    private val maxEntries: Int = 256
) {
    private val recent = mutableMapOf<Long, Long>()

    init {
        require(freshnessWindowMs > 0L && maxEntries > 0)
    }

    @Synchronized fun checkAndRemember(
        sample: BehaviorSample,
        nowMs: Long,
        isEnrollment: Boolean
    ): BehaviorReplayDecision {
        if (sample.qualityIssue() != BehaviorQualityIssue.NONE || nowMs < 0L || recent.values.any { it > nowMs }) {
            return BehaviorReplayDecision.REJECT_INVALID_SAMPLE
        }
        recent.entries.removeAll { nowMs - it.value >= freshnessWindowMs }
        val fingerprint = fingerprintBehaviorSample(sample)
        val previousAtMs = recent[fingerprint]
        if (!isEnrollment && evaluateBehaviorReplay(
                previousFingerprint = previousAtMs?.let { fingerprint },
                currentFingerprint = fingerprint,
                nowMs = nowMs,
                previousAtMs = previousAtMs ?: 0L,
                freshnessWindowMs = freshnessWindowMs
            ) == BehaviorReplayDecision.REJECT_REPLAY
        ) {
            return BehaviorReplayDecision.REJECT_REPLAY
        }
        // Do not evict a still-recent capture: A -> many other samples -> A must stay rejected.
        if (previousAtMs == null && recent.size >= maxEntries) return BehaviorReplayDecision.REJECT_CAPACITY
        recent[fingerprint] = nowMs
        return BehaviorReplayDecision.ACCEPT
    }
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
