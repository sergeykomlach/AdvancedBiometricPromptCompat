package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTemplateMatcherTest {
    @Test
    fun usesGmmScoreAsAuthoritativeWhenModelAndProbeFramesExist() {
        val template = VoiceTemplate(
            tag = "tag",
            phrase = null,
            embedding = embedding(1f, 0f),
            gmmModel = GmmVoiceModel(
                weights = floatArrayOf(1f),
                means = listOf(floatArrayOf(10f, 10f)),
                variances = listOf(floatArrayOf(0.01f, 0.01f)),
                enrollmentLogLikelihood = 0f,
                enrollmentLogLikelihoodStd = 0.5f
            )
        )

        val score = matchVoiceTemplates(
            enrolledTemplates = listOf(template),
            probeEmbedding = embedding(1f, 0f),
            probeFrames = listOf(floatArrayOf(-10f, -10f)),
            topK = 3
        )

        assertEquals(0f, score)
    }

    @Test
    fun fallsBackToEmbeddingWhenNoUsableGmmModelExists() {
        val score = matchVoiceTemplates(
            enrolledTemplates = listOf(VoiceTemplate("tag", null, embedding(1f, 0f))),
            probeEmbedding = embedding(0.99f, 0.01f),
            probeFrames = listOf(floatArrayOf(-10f, -10f)),
            topK = 3
        )

        assertTrue(score > 0.95f)
    }

    @Test
    fun detailedMatchExposesSafeDiagnosticsWithoutEmbeddingValues() {
        val model = GmmVoiceModel(
            weights = floatArrayOf(1f),
            means = listOf(floatArrayOf(0f, 0f)),
            variances = listOf(floatArrayOf(1f, 1f)),
            enrollmentLogLikelihood = -2f,
            enrollmentLogLikelihoodStd = 0.5f
        )

        val match = matchVoiceTemplatesDetailed(
            enrolledTemplates = listOf(VoiceTemplate("tag", null, embedding(1f, 0f), model)),
            probeEmbedding = embedding(0f, 1f),
            probeFrames = listOf(FloatArray(2) { 0f }),
            topK = 3
        )

        assertEquals(VoiceMatchMethod.GMM, match.method)
        assertEquals(1, match.gmmModelCount)
        assertEquals(1, match.probeFrameCount)
        assertTrue(match.gmmDetails != null)
    }

    private fun embedding(first: Float, second: Float): FloatArray {
        return floatArrayOf(first, second, 0f, 0f, 0f, 0f, 0f, 0f)
    }
}
