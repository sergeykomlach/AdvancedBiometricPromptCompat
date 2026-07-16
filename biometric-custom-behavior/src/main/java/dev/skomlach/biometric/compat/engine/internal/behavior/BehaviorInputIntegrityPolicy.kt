@file:Suppress("MatchingDeclarationName")

package dev.skomlach.biometric.compat.engine.internal.behavior

import kotlin.math.abs

internal enum class BehaviorInputIntegrityDecision {
    ACCEPT,
    NON_MONOTONIC_TIMESTAMPS,
    NEGATIVE_DWELL,
    INVALID_POINT,
    STALE_CAPTURE,
    DUPLICATE_CAPTURE,
    CANCELLED_CAPTURE
}

@Suppress("LongParameterList", "ReturnCount")
internal fun evaluateTypingIntegrity(
    downs: List<Long>,
    ups: List<Long>,
    phraseLength: Int,
    startedAtMs: Long,
    nowMs: Long,
    maxInterEventGapMs: Long
): BehaviorInputIntegrityDecision {
    if (downs.isEmpty() || downs.size != ups.size || phraseLength < 0) {
        return BehaviorInputIntegrityDecision.NON_MONOTONIC_TIMESTAMPS
    }
    if (downs.first() < startedAtMs || ups.last() > nowMs) {
        return BehaviorInputIntegrityDecision.NON_MONOTONIC_TIMESTAMPS
    }
    if (downs.zip(ups).any { (down, up) -> up < down }) {
        return BehaviorInputIntegrityDecision.NEGATIVE_DWELL
    }
    if (downs.zipWithNext().any { (left, right) -> right < left } ||
        ups.zipWithNext().any { (left, right) -> right < left }
    ) {
        return BehaviorInputIntegrityDecision.NON_MONOTONIC_TIMESTAMPS
    }
    val lastEventAtMs = maxOf(downs.last(), ups.last())
    if (nowMs - lastEventAtMs > maxInterEventGapMs) {
        return BehaviorInputIntegrityDecision.STALE_CAPTURE
    }
    if (abs(phraseLength - downs.size) > 2) {
        return BehaviorInputIntegrityDecision.NON_MONOTONIC_TIMESTAMPS
    }
    return BehaviorInputIntegrityDecision.ACCEPT
}

@Suppress("CyclomaticComplexMethod", "ComplexCondition", "ReturnCount")
internal fun evaluateSignatureIntegrity(
    points: List<BehaviorPoint>,
    startedAtMs: Long,
    nowMs: Long,
    cancelled: Boolean
): BehaviorInputIntegrityDecision {
    if (cancelled) return BehaviorInputIntegrityDecision.CANCELLED_CAPTURE
    if (points.isEmpty()) return BehaviorInputIntegrityDecision.INVALID_POINT
    if (points.any { point ->
            !point.x.isFinite() ||
                !point.y.isFinite() ||
                !point.timestampMs.isBetween(startedAtMs, nowMs) ||
                abs(point.x) > MAX_COORDINATE_ABS ||
                abs(point.y) > MAX_COORDINATE_ABS ||
                point.pressure?.let { it < 0f || !it.isFinite() } == true ||
                point.size?.let { it < 0f || !it.isFinite() } == true ||
                point.strokeId < 0
        }
    ) {
        return BehaviorInputIntegrityDecision.INVALID_POINT
    }
    if (points.zipWithNext().any { (left, right) -> right.timestampMs < left.timestampMs }) {
        return BehaviorInputIntegrityDecision.NON_MONOTONIC_TIMESTAMPS
    }
    return BehaviorInputIntegrityDecision.ACCEPT
}

private fun Long.isBetween(start: Long, end: Long): Boolean = this >= start && this <= end

private const val MAX_COORDINATE_ABS = 100_000f
