package dev.skomlach.biometric.compat.engine.internal.voice

import kotlin.math.abs
import kotlin.math.sqrt

class BasicVoiceEngine : VoiceEngine {
    override fun isAvailable(): Boolean = true

    override fun extractEmbedding(sample: VoiceSample): VoiceEmbeddingResult? {
        val providedEmbedding = sample.embedding
        if (providedEmbedding != null) {
            return providedEmbedding
                .normalize()
                ?.let { VoiceEmbeddingResult(it) }
        }

        val qualityIssue = sample.qualityIssue()
        if (qualityIssue != VoiceQualityIssue.NONE) {
            return VoiceEmbeddingResult(FloatArray(0), qualityIssue)
        }

        val pcm = sample.pcmFloat ?: return null
        val preprocessResult = VoiceAudioPreprocessor.preprocess(pcm, sample.sampleRateHz)
        if (preprocessResult.qualityIssue != VoiceQualityIssue.NONE) {
            return VoiceEmbeddingResult(
                FloatArray(0),
                preprocessResult.qualityIssue,
                preprocessMetrics = preprocessResult.metrics
            )
        }

        val frames = frameFeatures(preprocessResult.pcm, FRAME_COUNT)
        return frames.normalize()?.let { VoiceEmbeddingResult(it, preprocessMetrics = preprocessResult.metrics) }
    }

    private fun frameFeatures(pcm: FloatArray, frameCount: Int): FloatArray {
        val result = FloatArray(frameCount * FEATURES_PER_FRAME)
        for (frame in 0 until frameCount) {
            val start = frame * pcm.size / frameCount
            val end = ((frame + 1) * pcm.size / frameCount).coerceAtMost(pcm.size)
            if (end <= start) continue

            var energy = 0.0
            var zeroCrossings = 0
            var absoluteDelta = 0.0
            var peak = 0f
            var previous = pcm[start].coerceIn(-1f, 1f)
            for (index in start until end) {
                val value = pcm[index].coerceIn(-1f, 1f)
                energy += value * value
                peak = kotlin.math.max(peak, abs(value))
                if (index > start) {
                    if ((previous >= 0f) != (value >= 0f)) zeroCrossings++
                    absoluteDelta += abs(value - previous)
                }
                previous = value
            }

            val length = (end - start).coerceAtLeast(1)
            val base = frame * FEATURES_PER_FRAME
            result[base] = sqrt(energy / length).toFloat()
            result[base + 1] = zeroCrossings.toFloat() / length
            result[base + 2] = (absoluteDelta / length).toFloat()
            result[base + 3] = peak
        }
        return result
    }

    private fun FloatArray.normalize(): FloatArray? {
        if (!isValidEmbedding()) return null
        var sumSquares = 0.0
        for (value in this) {
            sumSquares += value * value
        }
        val norm = sqrt(sumSquares).toFloat()
        if (norm <= 0f || !norm.isFinite()) return null
        return FloatArray(size) { index -> this[index] / norm }
    }

    private companion object {
        const val FRAME_COUNT = 12
        const val FEATURES_PER_FRAME = 4
    }
}
