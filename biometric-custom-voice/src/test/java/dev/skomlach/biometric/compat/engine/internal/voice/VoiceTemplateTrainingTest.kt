package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTemplateTrainingTest {
    @Test
    fun trainVoiceTemplatesFiltersOutlierAndAddsCentroid() {
        val trained = trainVoiceTemplates(
            tag = "tag",
            phrase = null,
            embeddings = listOf(
                embedding(1f, 0f),
                embedding(0.98f, 0.02f),
                embedding(-1f, 0f)
            )
        )

        assertEquals(3, trained.size)
        assertTrue(trained.none { VoiceScorer.score(it.embedding, embedding(-1f, 0f)) > 0.90f })
        assertTrue(trained.any { VoiceScorer.score(it.embedding, embedding(1f, 0f)) > 0.99f })
    }

    @Test
    fun trainVoiceTemplatesKeepsSingleValidEmbeddingWithoutCentroid() {
        val trained = trainVoiceTemplates(
            tag = "tag",
            phrase = "hello",
            embeddings = listOf(embedding(1f, 0f))
        )

        assertEquals(1, trained.size)
        assertEquals("hello", trained.first().phrase)
    }

    @Test
    fun trainVoiceTemplatesAttachesGmmModelForSingleEmbeddingWhenFramesAreAvailable() {
        val trained = trainVoiceTemplates(
            tag = "tag",
            phrase = null,
            embeddings = listOf(embedding(1f, 0f)),
            featureBatches = listOf(List(40) { frame ->
                FloatArray(13) { coefficient -> frame / 100f + coefficient / 1000f }
            })
        )

        assertEquals(1, trained.size)
        assertTrue(trained.first().gmmModel != null)
    }

    private fun embedding(first: Float, second: Float): FloatArray {
        return floatArrayOf(first, second, 0f, 0f, 0f, 0f, 0f, 0f)
    }
}
