package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTemplateTrainingTest {
    @Test
    fun filtersOutlierAndAddsCentroid() {
        val trained = trainVoiceTemplates("tag", null, listOf(
            embedding(1f, 0f), embedding(0.98f, 0.02f), embedding(-1f, 0f)
        ))
        assertEquals(3, trained.size)
        assertTrue(trained.none { VoiceScorer.score(it.embedding, embedding(-1f, 0f)) > 0.90f })
    }

    @Test
    fun singleSampleRemainsSupported() {
        assertEquals(1, trainVoiceTemplates("tag", "hello", listOf(embedding(1f, 0f))).size)
    }

    @Test
    fun contradictoryRecordingsDoNotBecomeAnEnrollment() {
        assertTrue(trainVoiceTemplates("tag", null, listOf(embedding(1f, 0f), embedding(-1f, 0f))).isEmpty())
    }

    @Test
    fun equallySizedSpeakerGroupsAreRejected() {
        assertTrue(trainVoiceTemplates("tag", null, listOf(
            embedding(1f, 0f), embedding(0.98f, 0.02f),
            embedding(-1f, 0f), embedding(-0.98f, 0.02f)
        )).isEmpty())
    }

    @Test
    fun bridgingSampleDoesNotJoinInconsistentSpeakers() {
        // Both endpoints match the middle, but do not match each other.
        assertTrue(trainVoiceTemplates("tag", null, listOf(
            embedding(0.8f, -0.6f), embedding(1f, 0f), embedding(0.8f, 0.6f)
        )).isEmpty())
    }

    @Test
    fun invalidSampleCannotSilentlyTurnABatchIntoSingleSampleEnrollment() {
        assertTrue(trainVoiceTemplates("tag", null, listOf(
            embedding(1f, 0f), FloatArray(8)
        )).isEmpty())
    }

    @Test
    fun gmmUsesOnlyFramesOfAcceptedRecordingsAtTheirOriginalIndices() {
        val trained = trainVoiceTemplates("tag", null, listOf(
            embedding(1f, 0f), embedding(-1f, 0f), embedding(0.98f, 0.02f)
        ), listOf(frames(0.1f), frames(100f), frames(0.2f)))
        assertEquals(3, trained.size)
        val model = trained.single { it.gmmModel != null }.gmmModel!!
        assertTrue(model.means.all { mean -> mean.all { it < 1f } })
    }

    @Test
    fun featureBatchCountMustMatchRecordingCount() {
        assertTrue(trainVoiceTemplates("tag", null, listOf(
            embedding(1f, 0f), embedding(0.98f, 0.02f)
        ), listOf(frames(0.1f))).isEmpty())
    }

    @Test
    fun incompleteFeatureBatchesCannotTrainAnotherScoringMethodSilently() {
        assertTrue(trainVoiceTemplates("tag", null, listOf(
            embedding(1f, 0f), embedding(0.98f, 0.02f)
        ), listOf(frames(0.1f), emptyList())).isEmpty())
    }

    @Test
    fun inconsistentFeatureDimensionsAreRejected() {
        assertTrue(trainVoiceTemplates("tag", null, listOf(embedding(1f, 0f)),
            listOf(frames(0.1f) + listOf(FloatArray(12) { 0.1f }))
        ).isEmpty())
    }

    @Test
    fun enginesWithoutFrameFeaturesCanTrainAConsistentBatch() {
        val trained = trainVoiceTemplates("tag", null, listOf(
            embedding(1f, 0f), embedding(0.98f, 0.02f)
        ), listOf(emptyList(), emptyList()))
        assertEquals(3, trained.size)
        assertTrue(trained.all { it.gmmModel == null })
    }

    @Test(expected = IllegalArgumentException::class)
    fun publicSaveDoesNotReportATagWhenTrainingRejectedTheRecordings() {
        val engine = object : VoiceEngine, VoiceTemplateIdentityProvider {
            override val templateIdentity = "test-space"
            override fun isAvailable() = true
            override fun extractEmbedding(sample: VoiceSample): VoiceEmbeddingResult? = null
        }
        VoiceTemplateStore(engine).saveAll("tag", null, listOf(embedding(1f, 0f), embedding(-1f, 0f)))
    }

    private fun frames(value: Float) = List(40) { frame ->
        FloatArray(13) { coefficient -> value + frame / 1000f + coefficient / 10000f }
    }

    private fun embedding(first: Float, second: Float) =
        floatArrayOf(first, second, 0f, 0f, 0f, 0f, 0f, 0f)
}
