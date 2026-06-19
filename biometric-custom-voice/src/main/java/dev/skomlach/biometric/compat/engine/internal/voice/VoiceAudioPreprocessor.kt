package dev.skomlach.biometric.compat.engine.internal.voice

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class VoicePreprocessResult(
    val pcm: FloatArray,
    val qualityIssue: VoiceQualityIssue,
    val metrics: VoicePreprocessMetrics
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoicePreprocessResult
        return pcm.contentEquals(other.pcm) && qualityIssue == other.qualityIssue
    }

    override fun hashCode(): Int {
        return 31 * pcm.contentHashCode() + qualityIssue.hashCode()
    }
}

data class VoicePreprocessMetrics(
    val rawDurationMs: Long,
    val voicedDurationMs: Long,
    val noiseFloor: Float,
    val voicedFrameCount: Int,
    val clippedRatio: Float,
    val repeatedChunkRatio: Float
) {
    fun toLogString(): String {
        return "rawMs=$rawDurationMs voicedMs=$voicedDurationMs " +
            "noiseFloor=$noiseFloor voicedFrames=$voicedFrameCount " +
            "clipped=$clippedRatio repeated=$repeatedChunkRatio"
    }
}

internal object VoiceAudioPreprocessor {
    fun preprocess(pcm: FloatArray, sampleRateHz: Int): VoicePreprocessResult {
        val rawDurationMs = if (sampleRateHz > 0) {
            pcm.size * 1000L / sampleRateHz
        } else {
            0L
        }
        val emptyMetrics = VoicePreprocessMetrics(rawDurationMs, 0L, 0f, 0, 0f, 0f)
        if (pcm.isEmpty() || sampleRateHz <= 0) {
            return VoicePreprocessResult(FloatArray(0), VoiceQualityIssue.SAMPLE_MISSING, emptyMetrics)
        }

        val clippedRatio = clippedRatio(pcm)
        if (clippedRatio > MAX_CLIPPED_RATIO) {
            return VoicePreprocessResult(
                pcm.copyOf(),
                VoiceQualityIssue.SAMPLE_CLIPPED,
                emptyMetrics.copy(clippedRatio = clippedRatio)
            )
        }

        val cleaned = removeDcOffset(pcm)
        val frameLength = (sampleRateHz * FRAME_MS / 1000).coerceAtLeast(MIN_FRAME_LENGTH)
        if (cleaned.isEmpty()) {
            return VoicePreprocessResult(cleaned, VoiceQualityIssue.SAMPLE_MISSING, emptyMetrics.copy(clippedRatio = clippedRatio))
        }
        if (cleaned.size < frameLength) {
            return VoicePreprocessResult(cleaned, VoiceQualityIssue.SAMPLE_TOO_SHORT, emptyMetrics.copy(clippedRatio = clippedRatio))
        }

        val frameRms = frameRms(cleaned, frameLength)
        val noiseFloor = estimateNoiseFloor(frameRms)
        val voicedThreshold = max(MIN_VOICE_RMS, noiseFloor * NOISE_MULTIPLIER)
        val voicedFrames = frameRms.indices.filter { frameRms[it] >= voicedThreshold }
        val baseMetrics = VoicePreprocessMetrics(
            rawDurationMs = rawDurationMs,
            voicedDurationMs = 0L,
            noiseFloor = noiseFloor,
            voicedFrameCount = voicedFrames.size,
            clippedRatio = clippedRatio,
            repeatedChunkRatio = 0f
        )
        if (voicedFrames.isEmpty()) {
            return VoicePreprocessResult(cleaned, VoiceQualityIssue.SAMPLE_TOO_QUIET, baseMetrics)
        }

        val firstVoicedFrame = (voicedFrames.first() - PADDING_FRAMES).coerceAtLeast(0)
        val lastVoicedFrame = (voicedFrames.last() + PADDING_FRAMES).coerceAtMost(frameRms.lastIndex)
        val start = firstVoicedFrame * frameLength
        val endExclusive = min(cleaned.size, (lastVoicedFrame + 1) * frameLength)
        val trimmed = cleaned.copyOfRange(start, endExclusive)
        val durationMs = trimmed.size * 1000L / sampleRateHz
        val repeatedChunkRatio = repeatedChunkRatio(trimmed, sampleRateHz)
        val metrics = baseMetrics.copy(
            voicedDurationMs = durationMs,
            repeatedChunkRatio = repeatedChunkRatio
        )
        if (durationMs < MIN_VOICED_DURATION_MS) {
            return VoicePreprocessResult(trimmed, VoiceQualityIssue.SAMPLE_TOO_SHORT, metrics)
        }
        if (durationMs > MAX_VOICED_DURATION_MS) {
            return VoicePreprocessResult(trimmed, VoiceQualityIssue.SAMPLE_TOO_LONG, metrics)
        }
        if (repeatedChunkRatio > MAX_REPEATED_CHUNK_RATIO) {
            return VoicePreprocessResult(trimmed, VoiceQualityIssue.SAMPLE_REPLAY_RISK, metrics)
        }

        val trimmedRms = rms(trimmed)
        if (trimmedRms < MIN_VOICE_RMS) {
            return VoicePreprocessResult(trimmed, VoiceQualityIssue.SAMPLE_TOO_QUIET, metrics)
        }
        if (dynamicRange(trimmed) < MIN_DYNAMIC_RANGE_AFTER_TRIM) {
            return VoicePreprocessResult(trimmed, VoiceQualityIssue.SAMPLE_TOO_FLAT, metrics)
        }
        return VoicePreprocessResult(trimmed, VoiceQualityIssue.NONE, metrics)
    }

    private fun removeDcOffset(pcm: FloatArray): FloatArray {
        var sum = 0.0
        for (value in pcm) {
            if (!value.isFinite()) return FloatArray(0)
            sum += value.coerceIn(-1f, 1f)
        }
        val mean = (sum / pcm.size).toFloat()
        return FloatArray(pcm.size) { index ->
            (pcm[index].coerceIn(-1f, 1f) - mean).coerceIn(-1f, 1f)
        }
    }

    private fun frameRms(pcm: FloatArray, frameLength: Int): FloatArray {
        val frameCount = (pcm.size + frameLength - 1) / frameLength
        return FloatArray(frameCount) { frameIndex ->
            val start = frameIndex * frameLength
            val end = min(pcm.size, start + frameLength)
            rms(pcm, start, end)
        }
    }

    private fun estimateNoiseFloor(frameRms: FloatArray): Float {
        val sorted = frameRms.copyOf()
        sorted.sort()
        val quietFrameCount = max(1, sorted.size / 5)
        var sum = 0.0
        for (index in 0 until quietFrameCount) {
            sum += sorted[index]
        }
        return (sum / quietFrameCount).toFloat()
    }

    private fun rms(pcm: FloatArray): Float = rms(pcm, 0, pcm.size)

    private fun rms(pcm: FloatArray, start: Int, end: Int): Float {
        if (start >= end) return 0f
        var sumSquares = 0.0
        for (index in start until end) {
            val value = pcm[index]
            sumSquares += value * value
        }
        return sqrt(sumSquares / (end - start)).toFloat()
    }

    private fun dynamicRange(pcm: FloatArray): Float {
        var minValue = Float.MAX_VALUE
        var maxValue = -Float.MAX_VALUE
        for (value in pcm) {
            minValue = min(minValue, value)
            maxValue = max(maxValue, value)
        }
        return maxValue - minValue
    }

    private fun clippedRatio(pcm: FloatArray): Float {
        var clipped = 0
        for (value in pcm) {
            val absolute = kotlin.math.abs(value)
            if (absolute >= CLIPPED_SAMPLE_ABS) clipped++
        }
        return clipped.toFloat() / pcm.size.coerceAtLeast(1)
    }

    private fun repeatedChunkRatio(pcm: FloatArray, sampleRateHz: Int): Float {
        val chunkLength = (sampleRateHz * REPLAY_CHUNK_MS / 1000).coerceAtLeast(MIN_FRAME_LENGTH)
        val chunkCount = pcm.size / chunkLength
        if (chunkCount < MIN_REPLAY_CHUNKS) return 0f

        var repeated = 0
        var comparisons = 0
        for (chunkIndex in 1 until chunkCount) {
            val previousStart = (chunkIndex - 1) * chunkLength
            val currentStart = chunkIndex * chunkLength
            var absoluteDelta = 0.0
            for (offset in 0 until chunkLength) {
                absoluteDelta += kotlin.math.abs(pcm[previousStart + offset] - pcm[currentStart + offset])
            }
            val averageDelta = absoluteDelta / chunkLength
            if (averageDelta < REPEATED_CHUNK_MAX_DELTA) repeated++
            comparisons++
        }
        return repeated.toFloat() / comparisons.coerceAtLeast(1)
    }

    private const val FRAME_MS = 20
    private const val MIN_FRAME_LENGTH = 160
    private const val PADDING_FRAMES = 2
    private const val MIN_VOICE_RMS = 0.012f
    private const val NOISE_MULTIPLIER = 2.5f
    private const val MIN_VOICED_DURATION_MS = 900
    private const val MAX_VOICED_DURATION_MS = 8_000
    private const val MIN_DYNAMIC_RANGE_AFTER_TRIM = 0.025f
    private const val CLIPPED_SAMPLE_ABS = 0.98f
    private const val MAX_CLIPPED_RATIO = 0.08f
    private const val REPLAY_CHUNK_MS = 100
    private const val MIN_REPLAY_CHUNKS = 6
    private const val REPEATED_CHUNK_MAX_DELTA = 1.0E-4
    private const val MAX_REPEATED_CHUNK_RATIO = 0.60f
}
