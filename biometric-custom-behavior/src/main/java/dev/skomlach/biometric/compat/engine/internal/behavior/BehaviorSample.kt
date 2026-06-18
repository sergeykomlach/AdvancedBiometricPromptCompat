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
    TYPING_TIMING_TOO_UNIFORM,
    SIGNATURE_SAMPLE_TOO_SHORT,
    SIGNATURE_PATH_TOO_SHORT,
    SIGNATURE_SHAPE_TOO_SMALL
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
        private const val MIN_TYPING_EVENTS = 3
        private const val MIN_SIGNATURE_POINTS = 8
        private const val MIN_PRODUCTION_TYPING_CHARS = 5
        private const val MIN_PRODUCTION_TYPING_EVENTS = 5
        private const val MIN_PRODUCTION_SIGNATURE_POINTS = 16
        private const val MIN_SIGNATURE_PATH_LENGTH_PX = 64.0
        private const val MIN_SIGNATURE_BOUNDS_PX = 24f

        fun fromBundle(extra: Bundle?): BehaviorSample? {
            if (extra == null) return null
            val modeName = extra.getString(EXTRA_BEHAVIOR_MODE)
            val mode = runCatching {
                BehaviorMode.valueOf(modeName ?: BehaviorMode.COMBINED.name)
            }.getOrDefault(BehaviorMode.COMBINED)
            val keyDowns = extra.getLongArray(EXTRA_BEHAVIOR_KEY_DOWNS)?.toList().orEmpty()
            val keyUps = extra.getLongArray(EXTRA_BEHAVIOR_KEY_UPS)?.toList().orEmpty()
            val points = parsePoints(
                extra.getFloatArray(EXTRA_BEHAVIOR_POINTS),
                extra.getInt(EXTRA_BEHAVIOR_POINTS_STRIDE, LEGACY_POINT_STRIDE)
            )
            return BehaviorSample(
                mode = mode,
                phrase = extra.getString(EXTRA_BEHAVIOR_PHRASE),
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
            val result = ArrayList<BehaviorPoint>(raw.size / stride)
            var index = 0
            while (index + stride <= raw.size) {
                result.add(
                    BehaviorPoint(
                        x = raw[index],
                        y = raw[index + 1],
                        timestampMs = raw[index + 2].toLong(),
                        pressure = raw[index + 3].takeIf { it >= 0f },
                        size = raw[index + 4].takeIf { it >= 0f },
                        strokeId = if (stride == POINT_STRIDE) raw[index + 5].toInt() else 0
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
        val pathLength = strokePoints
            .zipWithNext()
            .filter { (left, right) -> left.strokeId == right.strokeId }
            .sumOf { (left, right) ->
                kotlin.math.hypot((right.x - left.x).toDouble(), (right.y - left.y).toDouble())
            }
        if (pathLength < MIN_SIGNATURE_PATH_LENGTH_PX) {
            return BehaviorQualityIssue.SIGNATURE_PATH_TOO_SHORT
        }
        val width = strokePoints.maxOf { it.x } - strokePoints.minOf { it.x }
        val height = strokePoints.maxOf { it.y } - strokePoints.minOf { it.y }
        if (kotlin.math.max(width, height) < MIN_SIGNATURE_BOUNDS_PX) {
            return BehaviorQualityIssue.SIGNATURE_SHAPE_TOO_SMALL
        }
        return BehaviorQualityIssue.NONE
    }

}
