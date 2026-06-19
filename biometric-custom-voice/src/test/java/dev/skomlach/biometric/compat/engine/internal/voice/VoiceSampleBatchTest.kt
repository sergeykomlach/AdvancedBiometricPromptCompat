package dev.skomlach.biometric.compat.engine.internal.voice

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSampleBatchTest {
    @Test
    fun fromBundleSamplesReadsSingleLegacySample() {
        val extras = Bundle().apply {
            putInt(VoiceSample.EXTRA_VOICE_SAMPLE_RATE, 16_000)
            putFloatArray(VoiceSample.EXTRA_VOICE_PCM_FLOAT, usablePcm())
        }

        val samples = VoiceSample.fromBundleSamples(extras)

        assertEquals(1, samples.size)
        assertTrue(samples.first().phrase == null)
    }

    @Test
    fun fromBundleSamplesReadsEnrollmentBatch() {
        val extras = Bundle().apply {
            putInt(VoiceSample.EXTRA_VOICE_SAMPLE_RATE, 16_000)
            putInt(VoiceSample.EXTRA_VOICE_SAMPLE_COUNT, 3)
            putString(VoiceSample.EXTRA_VOICE_PHRASE, "  open sesame  ")
            putFloatArray("${VoiceSample.EXTRA_VOICE_PCM_FLOAT}.0", usablePcm(seed = 10))
            putFloatArray("${VoiceSample.EXTRA_VOICE_PCM_FLOAT}.1", usablePcm(seed = 20))
            putFloatArray("${VoiceSample.EXTRA_VOICE_PCM_FLOAT}.2", usablePcm(seed = 30))
        }

        val samples = VoiceSample.fromBundleSamples(extras)

        assertEquals(3, samples.size)
        assertTrue(samples.all { it.phrase == "open sesame" })
    }

    @Test
    fun fromBundleSamplesLimitsEnrollmentBatch() {
        val extras = Bundle().apply {
            putInt(VoiceSample.EXTRA_VOICE_SAMPLE_RATE, 16_000)
            putInt(VoiceSample.EXTRA_VOICE_SAMPLE_COUNT, 7)
            repeat(7) { index ->
                putFloatArray("${VoiceSample.EXTRA_VOICE_PCM_FLOAT}.$index", usablePcm(seed = index))
            }
        }

        val samples = VoiceSample.fromBundleSamples(extras)

        assertEquals(5, samples.size)
    }

    @Test
    fun fromBundleSamplesReadsEmbeddingOnlySample() {
        val embedding = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f)
        val extras = Bundle().apply {
            putFloatArray(VoiceSample.EXTRA_VOICE_EMBEDDING, embedding)
        }

        val samples = VoiceSample.fromBundleSamples(extras)

        assertEquals(1, samples.size)
        assertTrue(samples.first().pcmFloat == null)
        assertTrue(samples.first().embedding.contentEquals(embedding))
        assertNotSame(embedding, samples.first().embedding)
    }

    @Test
    fun fromBundleSamplesPrefersBatchOverEmbeddingWhenBothArePresent() {
        val extras = Bundle().apply {
            putInt(VoiceSample.EXTRA_VOICE_SAMPLE_RATE, 16_000)
            putInt(VoiceSample.EXTRA_VOICE_SAMPLE_COUNT, 2)
            putFloatArray("${VoiceSample.EXTRA_VOICE_PCM_FLOAT}.0", usablePcm(seed = 1))
            putFloatArray("${VoiceSample.EXTRA_VOICE_PCM_FLOAT}.1", usablePcm(seed = 2))
            putFloatArray(
                VoiceSample.EXTRA_VOICE_EMBEDDING,
                floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f)
            )
        }

        val samples = VoiceSample.fromBundleSamples(extras)

        assertEquals(2, samples.size)
        assertTrue(samples.all { it.embedding == null })
    }

    private fun usablePcm(seed: Int = 0): FloatArray {
        return FloatArray(16_000) { index ->
            (0.25f * kotlin.math.sin((index + seed) / 11.0)).toFloat()
        }
    }
}
