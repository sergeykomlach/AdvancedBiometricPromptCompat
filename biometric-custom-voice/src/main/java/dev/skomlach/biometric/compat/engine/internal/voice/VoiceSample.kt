package dev.skomlach.biometric.compat.engine.internal.voice

import android.os.Bundle
import kotlin.math.sqrt

enum class VoiceQualityIssue {
    NONE,
    SAMPLE_MISSING,
    SAMPLE_RATE_TOO_LOW,
    SAMPLE_TOO_SHORT,
    SAMPLE_TOO_LONG,
    SAMPLE_TOO_QUIET,
    SAMPLE_TOO_FLAT,
    EMBEDDING_INVALID
}

data class VoiceSample(
    val sampleRateHz: Int,
    val pcmFloat: FloatArray?,
    val embedding: FloatArray?,
    val phrase: String?
) {
    companion object {
        const val EXTRA_VOICE_SAMPLE_RATE = "voice.sample_rate"
        const val EXTRA_VOICE_PCM_FLOAT = "voice.pcm_float"
        const val EXTRA_VOICE_EMBEDDING = "voice.embedding"
        const val EXTRA_VOICE_PHRASE = "voice.phrase"
        const val EXTRA_VOICE_SAMPLE_COUNT = "voice.sample_count"

        private const val MIN_SAMPLE_RATE_HZ = 8_000
        private const val MIN_DURATION_MS = 900
        private const val MAX_DURATION_MS = 8_000
        private const val MIN_RMS = 0.012f
        private const val MIN_DYNAMIC_RANGE = 0.025f
        private const val MIN_EMBEDDING_SIZE = 8
        private const val MAX_EMBEDDING_SIZE = 1024

        fun fromBundle(extra: Bundle?): VoiceSample? {
            return fromBundleSamples(extra).firstOrNull()
        }

        fun fromBundleSamples(extra: Bundle?): List<VoiceSample> {
            if (extra == null) return emptyList()
            val embedding = extra.getFloatArray(EXTRA_VOICE_EMBEDDING)
                ?.copyOf()
                ?.takeIf { it.isNotEmpty() }
            val phrase = extra.getString(EXTRA_VOICE_PHRASE)?.trim()?.takeIf { it.isNotEmpty() }
            val batchCount = extra.getInt(EXTRA_VOICE_SAMPLE_COUNT, 0)
            if (batchCount > 0) {
                return (0 until batchCount.coerceAtMost(MAX_BATCH_SAMPLES))
                    .mapNotNull { index ->
                        extra.getFloatArray("$EXTRA_VOICE_PCM_FLOAT.$index")
                            ?.copyOf()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { pcm ->
                                VoiceSample(
                                    sampleRateHz = extra.getInt(EXTRA_VOICE_SAMPLE_RATE, 0),
                                    pcmFloat = pcm,
                                    embedding = null,
                                    phrase = phrase
                                )
                            }
                    }
            }

            val pcm = extra.getFloatArray(EXTRA_VOICE_PCM_FLOAT)
                ?.copyOf()
                ?.takeIf { it.isNotEmpty() }
            if (embedding == null && pcm == null) return emptyList()
            return listOf(VoiceSample(
                sampleRateHz = extra.getInt(EXTRA_VOICE_SAMPLE_RATE, 0),
                pcmFloat = pcm,
                embedding = embedding,
                phrase = phrase
            ))
        }

        private const val MAX_BATCH_SAMPLES = 5
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoiceSample
        return sampleRateHz == other.sampleRateHz &&
            pcmFloat.contentEqualsNullable(other.pcmFloat) &&
            embedding.contentEqualsNullable(other.embedding) &&
            phrase == other.phrase
    }

    override fun hashCode(): Int {
        var result = sampleRateHz
        result = 31 * result + (pcmFloat?.contentHashCode() ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + (phrase?.hashCode() ?: 0)
        return result
    }

    fun qualityIssue(): VoiceQualityIssue {
        val providedEmbedding = embedding
        if (providedEmbedding != null) {
            return if (providedEmbedding.isValidEmbedding()) {
                VoiceQualityIssue.NONE
            } else {
                VoiceQualityIssue.EMBEDDING_INVALID
            }
        }

        val pcm = pcmFloat ?: return VoiceQualityIssue.SAMPLE_MISSING
        if (sampleRateHz < MIN_SAMPLE_RATE_HZ) return VoiceQualityIssue.SAMPLE_RATE_TOO_LOW

        val durationMs = pcm.size * 1000L / sampleRateHz
        if (durationMs < MIN_DURATION_MS) return VoiceQualityIssue.SAMPLE_TOO_SHORT
        if (durationMs > MAX_DURATION_MS) return VoiceQualityIssue.SAMPLE_TOO_LONG

        var sumSquares = 0.0
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (value in pcm) {
            if (!value.isFinite()) return VoiceQualityIssue.SAMPLE_MISSING
            val clipped = value.coerceIn(-1f, 1f)
            sumSquares += clipped * clipped
            min = kotlin.math.min(min, clipped)
            max = kotlin.math.max(max, clipped)
        }
        val rms = sqrt(sumSquares / pcm.size).toFloat()
        if (rms < MIN_RMS) return VoiceQualityIssue.SAMPLE_TOO_QUIET
        if (max - min < MIN_DYNAMIC_RANGE) return VoiceQualityIssue.SAMPLE_TOO_FLAT
        return VoiceQualityIssue.NONE
    }
}

internal fun FloatArray?.contentEqualsNullable(other: FloatArray?): Boolean {
    if (this == null || other == null) return this === other
    return contentEquals(other)
}

internal fun FloatArray.isValidEmbedding(): Boolean {
    if (size < 8 || size > 1024) return false
    var sumSquares = 0.0
    for (value in this) {
        if (!value.isFinite()) return false
        sumSquares += value * value
    }
    return sumSquares > 0.000001
}
