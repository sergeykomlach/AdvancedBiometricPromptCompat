package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class BasicVoiceEngineTest {
    @Test
    fun scorerAcceptsSimilarPreparedEmbeddings() {
        val enrolled = floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f, 0.1f, 0.2f, 0.3f, 0.4f)
        val probe = floatArrayOf(0.21f, 0.29f, 0.41f, 0.49f, 0.11f, 0.19f, 0.31f, 0.39f)

        assertTrue(VoiceScorer.score(enrolled, probe) >= 0.98f)
    }

    @Test
    fun scorerRejectsOppositePreparedEmbeddings() {
        val enrolled = floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f, 0.1f, 0.2f, 0.3f, 0.4f)
        val probe = enrolled.map { -it }.toFloatArray()

        assertTrue(VoiceScorer.score(enrolled, probe) < 0.30f)
    }

    @Test
    fun qualityRejectsQuietPcm() {
        val sample = VoiceSample(
            sampleRateHz = 16_000,
            pcmFloat = FloatArray(16_000) { 0.0001f },
            embedding = null,
            phrase = "hello"
        )

        assertTrue(sample.qualityIssue() == VoiceQualityIssue.SAMPLE_TOO_QUIET)
    }

    @Test
    fun basicEngineBuildsValidEmbeddingFromPcm() {
        val sample = VoiceSample(
            sampleRateHz = 16_000,
            pcmFloat = FloatArray(16_000) { index ->
                (0.35f * sin(index / 12.0)).toFloat()
            },
            embedding = null,
            phrase = "hello"
        )

        val result = BasicVoiceEngine().extractEmbedding(sample)

        assertTrue(result?.qualityIssue == VoiceQualityIssue.NONE)
        assertTrue(result?.embedding?.isValidEmbedding() == true)
    }

    @Test
    fun basicEngineAcceptsPhraseLessSamplesForTextIndependentFlow() {
        val sample = VoiceSample(
            sampleRateHz = 16_000,
            pcmFloat = FloatArray(16_000) { index ->
                (0.35f * sin(index / 10.0)).toFloat()
            },
            embedding = null,
            phrase = null
        )

        val result = BasicVoiceEngine().extractEmbedding(sample)

        assertTrue(sample.qualityIssue() == VoiceQualityIssue.NONE)
        assertTrue(result?.embedding?.isValidEmbedding() == true)
    }
}
