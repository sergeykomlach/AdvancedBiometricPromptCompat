package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Bundle

data class BehaviorPoint(
    val x: Float,
    val y: Float,
    val timestampMs: Long,
    val pressure: Float? = null,
    val size: Float? = null,
    val strokeId: Int = 0
)

enum class BehaviorQualityIssue {
    NONE,
    TYPING_PHRASE_TOO_SHORT,
    TYPING_SAMPLE_TOO_SHORT,
    TYPING_EVENT_MISMATCH,
    TYPING_TIMING_TOO_FAST,
    TYPING_TIMING_TOO_UNIFORM,
    SIGNATURE_SAMPLE_TOO_SHORT,
    SIGNATURE_PATH_TOO_SHORT,
    SIGNATURE_SHAPE_TOO_SMALL,
    SIGNATURE_SHAPE_TOO_SIMPLE,
    SIGNATURE_DUPLICATE_POINTS
}

data class BehaviorSample(
    val mode: BehaviorMode,
    val phrase: String?,
    val keyDownTimesMs: List<Long>,
    val keyUpTimesMs: List<Long>,
    val strokePoints: List<BehaviorPoint>
) {
    companion object {
        const val EXTRA_BEHAVIOR_MODE = "behavior.mode"
        const val EXTRA_BEHAVIOR_PHRASE = "behavior.phrase"
        const val EXTRA_BEHAVIOR_KEY_DOWNS = "behavior.key_downs"
        const val EXTRA_BEHAVIOR_KEY_UPS = "behavior.key_ups"
        const val EXTRA_BEHAVIOR_POINTS = "behavior.points"
        const val EXTRA_BEHAVIOR_POINTS_STRIDE = "behavior.points_stride"

        private const val LEGACY_POINT_STRIDE = 5
        private const val POINT_STRIDE = 6
        private const val MAX_PHRASE_LENGTH = 256
        private const val MAX_TYPING_EVENTS = 512
        private const val MAX_SIGNATURE_POINTS = 2048
        private const val MAX_COORDINATE_ABS = 100_000f
        private const val MIN_TYPING_EVENTS = 3
        private const val MIN_SIGNATURE_POINTS = 8
        private const val MIN_PRODUCTION_TYPING_CHARS = 5
        private const val MIN_PRODUCTION_TYPING_EVENTS = 5
        private const val MIN_PRODUCTION_SIGNATURE_POINTS = 16
        private const val MIN_SIGNATURE_PATH_LENGTH_PX = 64.0
        private const val MIN_SIGNATURE_BOUNDS_PX = 24f
        private const val MAX_TYPING_EVENT_GAP = 2
        private const val MIN_AVERAGE_TYPING_STEP_MS = 25.0
        private const val MIN_TYPING_DURATION_MS = 180L
        private const val MAX_SIGNATURE_DIRECTNESS = 0.96
        private const val MAX_SIGNATURE_DUPLICATE_RATIO = 0.28

        fun fromBundle(extra: Bundle?): BehaviorSample? {
            if (extra == null) return null
            val modeName = extra.getString(EXTRA_BEHAVIOR_MODE)
            val mode = runCatching {
                BehaviorMode.valueOf(modeName ?: BehaviorMode.COMBINED.name)
            }.getOrDefault(BehaviorMode.COMBINED)
            val keyDowns = extra.getLongArray(EXTRA_BEHAVIOR_KEY_DOWNS)
                ?.takeIf { it.size <= MAX_TYPING_EVENTS }
                ?.toList()
                .orEmpty()
            val keyUps = extra.getLongArray(EXTRA_BEHAVIOR_KEY_UPS)
                ?.takeIf { it.size <= MAX_TYPING_EVENTS }
                ?.toList()
                .orEmpty()
            val points = parsePoints(
                extra.getFloatArray(EXTRA_BEHAVIOR_POINTS),
                extra.getInt(EXTRA_BEHAVIOR_POINTS_STRIDE, LEGACY_POINT_STRIDE)
            )
            return BehaviorSample(
                mode = mode,
                phrase = extra.getString(EXTRA_BEHAVIOR_PHRASE)
                    ?.trim()
                    ?.take(MAX_PHRASE_LENGTH)
                    ?.takeIf { it.isNotEmpty() },
                keyDownTimesMs = keyDowns,
                keyUpTimesMs = keyUps,
                strokePoints = points
            ).takeIf { it.hasRequiredDataForMode() }
        }

        private fun parsePoints(raw: FloatArray?, requestedStride: Int): List<BehaviorPoint> {
            if (raw == null || raw.size < LEGACY_POINT_STRIDE) return emptyList()
            val stride = requestedStride
                .takeIf { it == POINT_STRIDE && raw.size % POINT_STRIDE == 0 }
                ?: LEGACY_POINT_STRIDE
            if (raw.size / stride > MAX_SIGNATURE_POINTS) return emptyList()
            val result = ArrayList<BehaviorPoint>(raw.size / stride)
            var index = 0
            while (index + stride <= raw.size) {
                val x = raw[index]
                val y = raw[index + 1]
                val timestamp = raw[index + 2]
                val pressure = raw[index + 3]
                val size = raw[index + 4]
                val stroke = if (stride == POINT_STRIDE) raw[index + 5] else 0f
                if (!x.isFinite() ||
                    !y.isFinite() ||
                    !timestamp.isFinite() ||
                    !pressure.isFinite() ||
                    !size.isFinite() ||
                    !stroke.isFinite() ||
                    kotlin.math.abs(x) > MAX_COORDINATE_ABS ||
                    kotlin.math.abs(y) > MAX_COORDINATE_ABS ||
                    timestamp < 0f
                ) {
                    return emptyList()
                }
                result.add(
                    BehaviorPoint(
                        x = x,
                        y = y,
                        timestampMs = timestamp.toLong(),
                        pressure = pressure.takeIf { it >= 0f },
                        size = size.takeIf { it >= 0f },
                        strokeId = stroke.toInt()
                    )
                )
                index += stride
            }
            return result
        }
    }

    fun hasRequiredDataForMode(): Boolean {
        val hasTyping = phrase?.isNotBlank() == true &&
            keyDownTimesMs.size >= MIN_TYPING_EVENTS &&
            keyDownTimesMs.size == keyUpTimesMs.size &&
            keyDownTimesMs.zip(keyUpTimesMs).all { (down, up) -> up >= down } &&
            keyDownTimesMs.zipWithNext().all { (left, right) -> right >= left }
        val hasSignature = strokePoints.size >= MIN_SIGNATURE_POINTS &&
            strokePoints.zipWithNext().all { (left, right) -> right.timestampMs >= left.timestampMs }
        return when (mode) {
            BehaviorMode.TYPING -> hasTyping
            BehaviorMode.SIGNATURE -> hasSignature
            BehaviorMode.COMBINED -> hasTyping && hasSignature
        }
    }

    fun qualityIssue(): BehaviorQualityIssue {
        return when (mode) {
            BehaviorMode.TYPING -> typingQualityIssue()
            BehaviorMode.SIGNATURE -> signatureQualityIssue()
            BehaviorMode.COMBINED -> {
                val typingIssue = typingQualityIssue()
                if (typingIssue != BehaviorQualityIssue.NONE) {
                    typingIssue
                } else {
                    signatureQualityIssue()
                }
            }
        }
    }

    fun metrics(): BehaviorSampleMetrics {
        val typingDurationMs = if (keyDownTimesMs.isNotEmpty() && keyUpTimesMs.isNotEmpty()) {
            keyUpTimesMs.last() - keyDownTimesMs.first()
        } else {
            0L
        }
        val strokeCount = strokePoints.map { it.strokeId }.distinct().size
        val signatureDurationMs = if (strokePoints.isNotEmpty()) {
            strokePoints.last().timestampMs - strokePoints.first().timestampMs
        } else {
            0L
        }
        val signaturePathLength = signaturePathLength()
        val signatureWidth = if (strokePoints.isNotEmpty()) {
            strokePoints.maxOf { it.x } - strokePoints.minOf { it.x }
        } else {
            0f
        }
        val signatureHeight = if (strokePoints.isNotEmpty()) {
            strokePoints.maxOf { it.y } - strokePoints.minOf { it.y }
        } else {
            0f
        }
        return BehaviorSampleMetrics(
            mode = mode,
            phraseLength = phrase?.trim()?.length ?: 0,
            keyEventCount = keyDownTimesMs.size,
            typingDurationMs = typingDurationMs.coerceAtLeast(0L),
            signaturePointCount = strokePoints.size,
            signatureStrokeCount = strokeCount,
            signatureDurationMs = signatureDurationMs.coerceAtLeast(0L),
            signaturePathLengthBucket = signaturePathLength.bucket(),
            signatureBoundsBucket = kotlin.math.max(signatureWidth, signatureHeight).toDouble().bucket()
        )
    }

    private fun typingQualityIssue(): BehaviorQualityIssue {
        val normalizedPhrase = phrase?.trim().orEmpty()
        if (normalizedPhrase.length < MIN_PRODUCTION_TYPING_CHARS) {
            return BehaviorQualityIssue.TYPING_PHRASE_TOO_SHORT
        }
        if (keyDownTimesMs.size < MIN_PRODUCTION_TYPING_EVENTS ||
            keyDownTimesMs.size != keyUpTimesMs.size
        ) {
            return BehaviorQualityIssue.TYPING_SAMPLE_TOO_SHORT
        }
        if (keyDownTimesMs.zipWithNext().any { (left, right) -> right < left } ||
            keyUpTimesMs.zipWithNext().any { (left, right) -> right < left } ||
            keyDownTimesMs.zip(keyUpTimesMs).any { (down, up) -> up <= down }
        ) {
            return BehaviorQualityIssue.TYPING_SAMPLE_TOO_SHORT
        }
        if (kotlin.math.abs(normalizedPhrase.length - keyDownTimesMs.size) > MAX_TYPING_EVENT_GAP) {
            return BehaviorQualityIssue.TYPING_EVENT_MISMATCH
        }
        val durationMs = keyUpTimesMs.last() - keyDownTimesMs.first()
        if (durationMs < MIN_TYPING_DURATION_MS ||
            durationMs.toDouble() / keyDownTimesMs.size < MIN_AVERAGE_TYPING_STEP_MS
        ) {
            return BehaviorQualityIssue.TYPING_TIMING_TOO_FAST
        }
        val dwellTimes = keyDownTimesMs.indices.map { keyUpTimesMs[it] - keyDownTimesMs[it] }
        val flightTimes = keyDownTimesMs.indices.drop(1).map { keyDownTimesMs[it] - keyUpTimesMs[it - 1] }
        val timingValues = dwellTimes + flightTimes
        if (timingValues.distinct().size <= 2) {
            return BehaviorQualityIssue.TYPING_TIMING_TOO_UNIFORM
        }
        return BehaviorQualityIssue.NONE
    }

    private fun signatureQualityIssue(): BehaviorQualityIssue {
        if (strokePoints.size < MIN_PRODUCTION_SIGNATURE_POINTS) {
            return BehaviorQualityIssue.SIGNATURE_SAMPLE_TOO_SHORT
        }
        val duplicateRatio = strokePoints.zipWithNext().count { (left, right) ->
            left.strokeId == right.strokeId && left.x == right.x && left.y == right.y
        }.toDouble() / (strokePoints.size - 1).coerceAtLeast(1)
        if (duplicateRatio > MAX_SIGNATURE_DUPLICATE_RATIO) {
            return BehaviorQualityIssue.SIGNATURE_DUPLICATE_POINTS
        }
        val pathLength = signaturePathLength()
        if (pathLength < MIN_SIGNATURE_PATH_LENGTH_PX) {
            return BehaviorQualityIssue.SIGNATURE_PATH_TOO_SHORT
        }
        val width = strokePoints.maxOf { it.x } - strokePoints.minOf { it.x }
        val height = strokePoints.maxOf { it.y } - strokePoints.minOf { it.y }
        if (kotlin.math.max(width, height) < MIN_SIGNATURE_BOUNDS_PX) {
            return BehaviorQualityIssue.SIGNATURE_SHAPE_TOO_SMALL
        }
        val directDistance = kotlin.math.hypot(
            (strokePoints.last().x - strokePoints.first().x).toDouble(),
            (strokePoints.last().y - strokePoints.first().y).toDouble()
        )
        if (pathLength > 0.0 && directDistance / pathLength > MAX_SIGNATURE_DIRECTNESS) {
            return BehaviorQualityIssue.SIGNATURE_SHAPE_TOO_SIMPLE
        }
        return BehaviorQualityIssue.NONE
    }

    private fun signaturePathLength(): Double {
        return strokePoints
            .zipWithNext()
            .filter { (left, right) -> left.strokeId == right.strokeId }
            .sumOf { (left, right) ->
                kotlin.math.hypot((right.x - left.x).toDouble(), (right.y - left.y).toDouble())
            }
    }

    private fun Double.bucket(): Int {
        return when {
            this <= 0.0 -> 0
            this < 50.0 -> 50
            this < 100.0 -> 100
            this < 250.0 -> 250
            this < 500.0 -> 500
            else -> 1000
        }
    }

}

data class BehaviorSampleMetrics(
    val mode: BehaviorMode,
    val phraseLength: Int,
    val keyEventCount: Int,
    val typingDurationMs: Long,
    val signaturePointCount: Int,
    val signatureStrokeCount: Int,
    val signatureDurationMs: Long,
    val signaturePathLengthBucket: Int,
    val signatureBoundsBucket: Int
) {
    fun toLogString(): String {
        return "mode=$mode phraseLen=$phraseLength keyEvents=$keyEventCount typingMs=$typingDurationMs " +
            "points=$signaturePointCount strokes=$signatureStrokeCount signatureMs=$signatureDurationMs " +
            "pathBucket=$signaturePathLengthBucket boundsBucket=$signatureBoundsBucket"
    }
}
