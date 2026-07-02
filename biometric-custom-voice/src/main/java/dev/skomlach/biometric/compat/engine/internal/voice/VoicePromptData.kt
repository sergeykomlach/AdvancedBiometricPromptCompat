package dev.skomlach.biometric.compat.engine.internal.voice

import android.os.Bundle

internal const val VOICE_EXTRA_SAMPLE_RATE = "voice.sample_rate"
internal const val VOICE_EXTRA_PCM_FLOAT = "voice.pcm_float"
internal const val VOICE_EXTRA_EMBEDDING = "voice.embedding"
internal const val VOICE_EXTRA_PHRASE = "voice.phrase"
internal const val VOICE_EXTRA_SAMPLE_COUNT = "voice.sample_count"

private const val MAX_VOICE_SAMPLE_COUNT = 5
private const val MAX_VOICE_PHRASE_LENGTH = 256

internal fun buildVoiceExtras(
    existing: Bundle?,
    phrase: CharSequence?,
    sampleRateHz: Int,
    pcmSamples: Collection<FloatArray>
): Bundle {
    val extras = Bundle(existing ?: Bundle())
    clearVoiceInput(extras)
    phrase
        ?.toString()
        ?.trim()
        ?.take(MAX_VOICE_PHRASE_LENGTH)
        ?.takeIf { it.isNotEmpty() }
        ?.let { extras.putString(VOICE_EXTRA_PHRASE, it) }
    val samples = pcmSamples
        .asSequence()
        .filter { it.isNotEmpty() }
        .take(MAX_VOICE_SAMPLE_COUNT)
        .map { it.copyOf() }
        .toList()
    if (samples.isEmpty()) {
        return extras
    }
    extras.putInt(VOICE_EXTRA_SAMPLE_RATE, sampleRateHz)
    if (samples.size == 1) {
        extras.putFloatArray(VOICE_EXTRA_PCM_FLOAT, samples.first())
    } else {
        extras.putInt(VOICE_EXTRA_SAMPLE_COUNT, samples.size)
        samples.forEachIndexed { index, sample ->
            extras.putFloatArray("$VOICE_EXTRA_PCM_FLOAT.$index", sample)
        }
    }
    return extras
}

internal fun hasVoiceInput(extras: Bundle?): Boolean {
    if (extras == null) return false
    val embedding = extras.getFloatArray(VOICE_EXTRA_EMBEDDING)
    if (embedding?.isNotEmpty() == true) {
        return true
    }
    val batchCount = extras.getInt(VOICE_EXTRA_SAMPLE_COUNT, 0)
    if (batchCount > 0) {
        return (0 until batchCount.coerceAtMost(MAX_VOICE_SAMPLE_COUNT)).any { index ->
            extras.getFloatArray("$VOICE_EXTRA_PCM_FLOAT.$index")?.isNotEmpty() == true
        }
    }
    return extras.getFloatArray(VOICE_EXTRA_PCM_FLOAT)?.isNotEmpty() == true
}

internal fun clearVoiceInput(extras: Bundle) {
    extras.remove(VOICE_EXTRA_SAMPLE_RATE)
    extras.remove(VOICE_EXTRA_PCM_FLOAT)
    extras.remove(VOICE_EXTRA_EMBEDDING)
    extras.remove(VOICE_EXTRA_SAMPLE_COUNT)
    repeat(MAX_VOICE_SAMPLE_COUNT) { index ->
        extras.remove("$VOICE_EXTRA_PCM_FLOAT.$index")
    }
}
