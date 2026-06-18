package dev.skomlach.biometric.compat.engine.internal.behavior

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class BehaviorScore(
    val total: Float,
    val typing: Float? = null,
    val signature: Float? = null,
    val reason: BehaviorScoreReason = BehaviorScoreReason.OK
)

enum class BehaviorScoreReason {
    OK,
    MODE_MISMATCH,
    PHRASE_MISMATCH,
    INCOMPLETE_SAMPLE,
    INCOMPATIBLE_FEATURES
}

object BehaviorScorer {
    private const val RESAMPLED_SIGNATURE_POINTS = 64
    private const val MIN_TIMING_BASELINE_MS = 35.0
    private const val MAX_TIMING_RATIO = 4.0
    private const val SIGNATURE_REJECT_DISTANCE = 0.42

    fun score(enrolled: BehaviorSample, probe: BehaviorSample): Float {
        return scoreDetails(enrolled, probe).total
    }

    fun scoreDetails(enrolled: BehaviorSample, probe: BehaviorSample): BehaviorScore {
        if (enrolled.mode != probe.mode) {
            return BehaviorScore(0f, reason = BehaviorScoreReason.MODE_MISMATCH)
        }
        if (enrolled.phrase != null && probe.phrase != null && enrolled.phrase != probe.phrase) {
            return BehaviorScore(0f, reason = BehaviorScoreReason.PHRASE_MISMATCH)
        }
        if (!enrolled.hasRequiredDataForMode() || !probe.hasRequiredDataForMode()) {
            return BehaviorScore(0f, reason = BehaviorScoreReason.INCOMPLETE_SAMPLE)
        }

        val score = when (enrolled.mode) {
            BehaviorMode.TYPING -> {
                val typing = typingScore(enrolled, probe)
                BehaviorScore(typing, typing = typing)
            }
            BehaviorMode.SIGNATURE -> {
                val signature = signatureScore(enrolled, probe)
                BehaviorScore(signature, signature = signature)
            }
            BehaviorMode.COMBINED -> {
                val typing = typingScore(enrolled, probe)
                val signature = signatureScore(enrolled, probe)
                if (typing <= 0f || signature <= 0f) {
                    BehaviorScore(
                        0f,
                        typing = typing,
                        signature = signature,
                        reason = BehaviorScoreReason.INCOMPATIBLE_FEATURES
                    )
                } else {
                    BehaviorScore(
                        total = (typing * 0.45f) + (signature * 0.55f),
                        typing = typing,
                        signature = signature
                    )
                }
            }
        }
        return score.copy(total = score.total.coerceIn(0f, 1f))
    }

    private fun typingScore(enrolled: BehaviorSample, probe: BehaviorSample): Float {
        val enrolledFeatures = typingFeatures(enrolled)
        val probeFeatures = typingFeatures(probe)
        if (enrolledFeatures.isEmpty() || enrolledFeatures.size != probeFeatures.size) return 0f

        val normalizedDistance = enrolledFeatures.indices.sumOf { index ->
            val baseline = max(MIN_TIMING_BASELINE_MS, abs(enrolledFeatures[index]))
            min(MAX_TIMING_RATIO, abs(enrolledFeatures[index] - probeFeatures[index]) / baseline)
        } / enrolledFeatures.size
        return (1.0 - (normalizedDistance / MAX_TIMING_RATIO)).toFloat()
    }

    private fun typingFeatures(sample: BehaviorSample): List<Double> {
        val downs = sample.keyDownTimesMs
        val ups = sample.keyUpTimesMs
        if (downs.size < 3 || downs.size != ups.size) return emptyList()

        val result = ArrayList<Double>(downs.size * 5)
        for (index in downs.indices) {
            result.add((ups[index] - downs[index]).toDouble())
            if (index == 0) continue
            result.add((downs[index] - downs[index - 1]).toDouble())
            result.add((downs[index] - ups[index - 1]).toDouble())
            result.add((ups[index] - ups[index - 1]).toDouble())
            result.add((ups[index] - downs[index - 1]).toDouble())
        }
        return result
    }

    private fun signatureScore(enrolled: BehaviorSample, probe: BehaviorSample): Float {
        val enrolledResampled = resampleByPathLength(enrolled.strokePoints)
        val probeResampled = resampleByPathLength(probe.strokePoints)
        val enrolledPoints = normalize(enrolledResampled)
        val probePoints = normalize(probeResampled)
        if (enrolledPoints.isEmpty() || probePoints.isEmpty()) return 0f

        val distance = dynamicTimeWarpingDistance(enrolledPoints, probePoints)
        val dtwScore = (1.0 - min(1.0, distance / SIGNATURE_REJECT_DISTANCE)).toFloat()
        val shapeScore = signatureGlobalScore(enrolledResampled, probeResampled)
        return ((dtwScore * 0.78f) + (shapeScore * 0.22f)).coerceIn(0f, 1f)
    }

    private fun resampleByPathLength(points: List<BehaviorPoint>): List<BehaviorPoint> {
        if (points.size < 2) return emptyList()
        val cumulative = DoubleArray(points.size)
        for (index in 1..points.lastIndex) {
            val segmentLength = if (points[index].strokeId == points[index - 1].strokeId) {
                hypot(
                    (points[index].x - points[index - 1].x).toDouble(),
                    (points[index].y - points[index - 1].y).toDouble()
                )
            } else {
                0.0
            }
            cumulative[index] = cumulative[index - 1] + segmentLength
        }
        val totalLength = cumulative.last()
        if (totalLength <= 0.0) return emptyList()

        val result = ArrayList<BehaviorPoint>(RESAMPLED_SIGNATURE_POINTS)
        var sourceIndex = 1
        for (targetIndex in 0 until RESAMPLED_SIGNATURE_POINTS) {
            val targetLength = (targetIndex * totalLength) / (RESAMPLED_SIGNATURE_POINTS - 1)
            while (sourceIndex < cumulative.lastIndex && cumulative[sourceIndex] < targetLength) {
                sourceIndex++
            }
            val previousIndex = (sourceIndex - 1).coerceAtLeast(0)
            val segmentLength = cumulative[sourceIndex] - cumulative[previousIndex]
            val ratio = if (segmentLength <= 0.0 ||
                points[sourceIndex].strokeId != points[previousIndex].strokeId
            ) {
                0f
            } else {
                ((targetLength - cumulative[previousIndex]) / segmentLength).toFloat()
            }
            val a = points[previousIndex]
            val b = points[sourceIndex]
            result.add(interpolate(a, b, ratio))
        }
        return result
    }

    private fun normalize(points: List<BehaviorPoint>): List<BehaviorPoint> {
        if (points.isEmpty()) return emptyList()
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val firstTime = points.first().timestampMs
        val duration = (points.last().timestampMs - firstTime).takeIf { it > 0L } ?: 1L
        val scale = max(maxX - minX, maxY - minY).takeIf { it > 0f } ?: return emptyList()

        return points.map {
            it.copy(
                x = (it.x - minX) / scale,
                y = (it.y - minY) / scale,
                timestampMs = (((it.timestampMs - firstTime).toDouble() / duration) * 1_000_000).toLong()
            )
        }
    }

    private fun dynamicTimeWarpingDistance(
        enrolled: List<BehaviorPoint>,
        probe: List<BehaviorPoint>
    ): Double {
        val window = max(8, max(enrolled.size, probe.size) / 4)
        var previous = DoubleArray(probe.size + 1) { Double.POSITIVE_INFINITY }
        var current = DoubleArray(probe.size + 1) { Double.POSITIVE_INFINITY }
        previous[0] = 0.0

        for (i in 1..enrolled.size) {
            current.fill(Double.POSITIVE_INFINITY)
            val start = max(1, i - window)
            val end = min(probe.size, i + window)
            for (j in start..end) {
                val cost = pointDistance(enrolled[i - 1], probe[j - 1])
                current[j] = cost + min(previous[j], min(current[j - 1], previous[j - 1]))
            }
            val swap = previous
            previous = current
            current = swap
        }

        val raw = previous[probe.size]
        if (!raw.isFinite()) return 1.0
        return raw / max(enrolled.size, probe.size)
    }

    private fun signatureGlobalScore(
        enrolled: List<BehaviorPoint>,
        probe: List<BehaviorPoint>
    ): Float {
        val enrolledFeatures = signatureGlobalFeatures(enrolled)
        val probeFeatures = signatureGlobalFeatures(probe)
        if (enrolledFeatures.isEmpty() || enrolledFeatures.size != probeFeatures.size) return 0f
        val distance = enrolledFeatures.indices.sumOf { index ->
            min(1.0, abs(enrolledFeatures[index] - probeFeatures[index]))
        } / enrolledFeatures.size
        return (1.0 - distance).toFloat()
    }

    private fun signatureGlobalFeatures(points: List<BehaviorPoint>): List<Double> {
        if (points.size < 2) return emptyList()
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = (maxX - minX).takeIf { it > 0f } ?: 1f
        val height = (maxY - minY).takeIf { it > 0f } ?: 1f
        val boxScale = max(width, height).toDouble()
        val duration = max(1L, points.last().timestampMs - points.first().timestampMs).toDouble()
        val pathLength = points.zipWithNext()
            .filter { (left, right) -> left.strokeId == right.strokeId }
            .sumOf { (left, right) ->
                hypot((right.x - left.x).toDouble(), (right.y - left.y).toDouble())
            }
        val directDistance = hypot(
            (points.last().x - points.first().x).toDouble(),
            (points.last().y - points.first().y).toDouble()
        )
        val strokeCount = points.map { it.strokeId }.distinct().size
        val pressureValues = points.mapNotNull { it.pressure?.toDouble() }
        val sizeValues = points.mapNotNull { it.size?.toDouble() }
        return listOf(
            min(1.0, width / boxScale),
            min(1.0, height / boxScale),
            min(1.0, directDistance / max(1.0, pathLength)),
            min(1.0, (pathLength / boxScale) / 8.0),
            min(1.0, (pathLength / duration) / 4.0),
            min(1.0, strokeCount / 6.0),
            pressureValues.averageOrNull() ?: 0.0,
            sizeValues.averageOrNull() ?: 0.0
        )
    }

    private fun pointDistance(left: BehaviorPoint, right: BehaviorPoint): Double {
        val spatial = hypot((left.x - right.x).toDouble(), (left.y - right.y).toDouble())
        val timing = abs(left.timestampMs - right.timestampMs) / 1_000_000.0
        val pressure = nullableDistance(left.pressure, right.pressure)
        val size = nullableDistance(left.size, right.size)
        val strokePenalty = if (left.strokeId == right.strokeId) 0.0 else 0.18
        return sqrt((spatial * spatial) + (0.18 * timing * timing)) +
            (0.08 * pressure) +
            (0.04 * size) +
            strokePenalty
    }

    private fun nullableDistance(left: Float?, right: Float?): Double {
        if (left == null || right == null) return 0.0
        return min(1.0, abs(left - right).toDouble())
    }

    private fun interpolate(a: BehaviorPoint, b: BehaviorPoint, ratio: Float): BehaviorPoint {
        return BehaviorPoint(
            x = a.x + (b.x - a.x) * ratio,
            y = a.y + (b.y - a.y) * ratio,
            timestampMs = (a.timestampMs + (b.timestampMs - a.timestampMs) * ratio).toLong(),
            pressure = interpolateNullable(a.pressure, b.pressure, ratio),
            size = interpolateNullable(a.size, b.size, ratio)
        )
    }

    private fun interpolateNullable(left: Float?, right: Float?, ratio: Float): Float? {
        if (left == null || right == null) return null
        return left + (right - left) * ratio
    }

    private fun List<Double>.averageOrNull(): Double? {
        if (isEmpty()) return null
        return average()
    }
}
