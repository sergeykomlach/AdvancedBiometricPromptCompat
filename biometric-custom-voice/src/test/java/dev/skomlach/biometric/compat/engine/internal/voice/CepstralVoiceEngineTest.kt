package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class CepstralVoiceEngineTest {
    @Test
    fun buildsStableNormalizedEmbeddingFromPcm() {
        val sample = VoiceSample(
            sampleRateHz = SAMPLE_RATE,
            pcmFloat = voiceLikeTone(180.0, 0.02f),
            embedding = null,
            phrase = null
        )

        val result = CepstralVoiceEngine().extractEmbedding(sample)

        assertEquals(VoiceQualityIssue.NONE, result?.qualityIssue)
        assertEquals(CEPSTRAL_EMBEDDING_SIZE, result?.embedding?.size)
        assertTrue(result?.embedding?.isValidEmbedding() == true)
        assertTrue(result?.featureFrames?.isNotEmpty() == true)
        assertTrue(result?.featureFrames?.all { frame -> frame.size == CEPSTRAL_FRAME_SIZE } == true)
        assertTrue(result?.embedding?.normDistanceFromUnit()?.let { it < 0.0001f } == true)
    }

    @Test
    fun scoresSimilarVoiceLikeSamplesHigherThanDifferentSpectralShape() {
        val engine = CepstralVoiceEngine()
        val enrolled = engine.extractEmbedding(
            VoiceSample(SAMPLE_RATE, voiceLikeTone(170.0, 0.015f), null, null)
        )?.embedding
        val similar = engine.extractEmbedding(
            VoiceSample(SAMPLE_RATE, voiceLikeTone(174.0, 0.018f), null, null)
        )?.embedding
        val different = engine.extractEmbedding(
            VoiceSample(SAMPLE_RATE, voiceLikeTone(430.0, 0.04f), null, null)
        )?.embedding

        requireNotNull(enrolled)
        requireNotNull(similar)
        requireNotNull(different)
        assertTrue(enrolled.isValidEmbedding())
        assertTrue(similar.isValidEmbedding())
        assertTrue(different.isValidEmbedding())

        val similarScore = VoiceScorer.score(enrolled, similar)
        val differentScore = VoiceScorer.score(enrolled, different)
        assertTrue(similarScore > 0.90f)
        assertTrue(differentScore < similarScore - 0.08f)
    }

    private fun voiceLikeTone(baseFrequency: Double, noiseScale: Float): FloatArray {
        return FloatArray(SAMPLE_RATE * 2) { index ->
            val time = index.toDouble() / SAMPLE_RATE
            val harmonic = 0.42 * sin(2.0 * PI * baseFrequency * time) +
                0.19 * sin(2.0 * PI * baseFrequency * 2.0 * time) +
                0.08 * sin(2.0 * PI * baseFrequency * 3.0 * time)
            val deterministicNoise = noiseScale * sin(index * 0.173)
            (harmonic + deterministicNoise).toFloat().coerceIn(-0.95f, 0.95f)
        }
    }

    private fun FloatArray.normDistanceFromUnit(): Float {
        val norm = kotlin.math.sqrt(sumOf { it * it.toDouble() }).toFloat()
        return kotlin.math.abs(1f - norm)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CEPSTRAL_FRAME_SIZE = 13
        const val CEPSTRAL_EMBEDDING_SIZE = 78
    }
}
