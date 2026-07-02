package dev.skomlach.biometric.compat.engine.internal.voice

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class VoiceConditionedChunk(
    val samples: FloatArray,
    val inputRms: Float,
    val conditionedRms: Float
)

class VoiceSignalConditioner(
    private val targetRms: Float = 0.045f,
    val silenceRmsThreshold: Float = 0.0035f,
    private val maxGain: Float = 4f
) {
    fun condition(chunk: FloatArray): VoiceConditionedChunk {
        if (chunk.isEmpty()) {
            return VoiceConditionedChunk(
                samples = FloatArray(0),
                inputRms = 0f,
                conditionedRms = 0f
            )
        }

        val dcOffset = chunk.average().toFloat()
        val centered = FloatArray(chunk.size) { index ->
            (chunk[index] - dcOffset).coerceIn(-1f, 1f)
        }
        val inputRms = rms(centered)
        if (inputRms <= 0f || inputRms <= silenceRmsThreshold) {
            return VoiceConditionedChunk(
                samples = centered,
                inputRms = inputRms,
                conditionedRms = inputRms
            )
        }

        val desiredGain = targetRms / inputRms
        val gain = min(max(desiredGain, 1f), maxGain)
        val conditioned = FloatArray(centered.size) { index ->
            (centered[index] * gain).coerceIn(-1f, 1f)
        }
        return VoiceConditionedChunk(
            samples = conditioned,
            inputRms = inputRms,
            conditionedRms = rms(conditioned)
        )
    }

    private fun rms(chunk: FloatArray): Float {
        if (chunk.isEmpty()) return 0f
        var sumSquares = 0.0
        for (value in chunk) {
            sumSquares += value * value
        }
        return sqrt(sumSquares / chunk.size).toFloat()
    }
}
