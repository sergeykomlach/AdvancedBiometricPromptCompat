package dev.skomlach.biometric.compat.engine.internal.voice

import android.os.Bundle
import org.junit.Assert.assertEquals
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
            putFloatArray("${VoiceSample.EXTRA_VOICE_PCM_FLOAT}.0", usablePcm(seed = 10))
            putFloatArray("${VoiceSample.EXTRA_VOICE_PCM_FLOAT}.1", usablePcm(seed = 20))
            putFloatArray("${VoiceSample.EXTRA_VOICE_PCM_FLOAT}.2", usablePcm(seed = 30))
        }

        val samples = VoiceSample.fromBundleSamples(extras)

        assertEquals(3, samples.size)
    }

    private fun usablePcm(seed: Int = 0): FloatArray {
        return FloatArray(16_000) { index ->
            (0.25f * kotlin.math.sin((index + seed) / 11.0)).toFloat()
        }
    }
}
