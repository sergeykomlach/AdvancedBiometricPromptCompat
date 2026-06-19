package dev.skomlach.biometric.compat.engine.internal.voice

import android.os.Bundle

enum class VoiceQualityIssue {
    NONE,
    SAMPLE_MISSING,
    SAMPLE_RATE_TOO_LOW,
    SAMPLE_TOO_SHORT,
    SAMPLE_TOO_LONG,
    SAMPLE_TOO_QUIET,
    SAMPLE_TOO_FLAT,
    SAMPLE_CLIPPED,
    SAMPLE_REPLAY_RISK,
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
        private const val MIN_EMBEDDING_SIZE = 8
        private const val MAX_EMBEDDING_SIZE = 1024
        private const val MAX_SAMPLE_RATE_HZ = 96_000
        private const val MAX_RAW_AUDIO_DURATION_MS = 12_000L
        private const val MAX_PHRASE_LENGTH = 256

        fun fromBundle(extra: Bundle?): VoiceSample? {
            return fromBundleSamples(extra).firstOrNull()
        }

        fun fromBundleSamples(extra: Bundle?): List<VoiceSample> {
            if (extra == null) return emptyList()
            val sampleRateHz = extra.getInt(EXTRA_VOICE_SAMPLE_RATE, 0)
            val embedding = extra.getFloatArray(EXTRA_VOICE_EMBEDDING)
                ?.takeIf { it.size in MIN_EMBEDDING_SIZE..MAX_EMBEDDING_SIZE }
                ?.copyOf()
            val phrase = extra.getString(EXTRA_VOICE_PHRASE)
                ?.trim()
                ?.take(MAX_PHRASE_LENGTH)
                ?.takeIf { it.isNotEmpty() }
            val batchCount = extra.getInt(EXTRA_VOICE_SAMPLE_COUNT, 0)
            if (batchCount > 0) {
                return (0 until batchCount.coerceAtMost(MAX_BATCH_SAMPLES))
                    .mapNotNull { index ->
                        safePcm(extra.getFloatArray("$EXTRA_VOICE_PCM_FLOAT.$index"), sampleRateHz)
                            ?.let { pcm ->
                                VoiceSample(
                                    sampleRateHz = sampleRateHz,
                                    pcmFloat = pcm,
                                    embedding = null,
                                    phrase = phrase
                                )
                            }
                    }
            }

            val pcm = safePcm(extra.getFloatArray(EXTRA_VOICE_PCM_FLOAT), sampleRateHz)
            if (embedding == null && pcm == null) return emptyList()
            return listOf(VoiceSample(
                sampleRateHz = sampleRateHz,
                pcmFloat = pcm,
                embedding = embedding,
                phrase = phrase
            ))
        }

        private fun safePcm(raw: FloatArray?, sampleRateHz: Int): FloatArray? {
            if (raw == null || raw.isEmpty()) return null
            if (sampleRateHz !in MIN_SAMPLE_RATE_HZ..MAX_SAMPLE_RATE_HZ) return null
            val maxSamples = ((sampleRateHz.toLong() * MAX_RAW_AUDIO_DURATION_MS) / 1000L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            if (raw.size > maxSamples) return null
            return raw.copyOf()
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
        for (value in pcm) {
            if (!value.isFinite()) return VoiceQualityIssue.SAMPLE_MISSING
        }
        return VoiceAudioPreprocessor.preprocess(pcm, sampleRateHz).qualityIssue
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
