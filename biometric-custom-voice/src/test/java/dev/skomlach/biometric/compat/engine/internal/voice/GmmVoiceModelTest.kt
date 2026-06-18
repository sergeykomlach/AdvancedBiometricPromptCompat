package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class GmmVoiceModelTest {
    @Test
    fun trainerBuildsModelThatScoresSimilarFramesAboveDifferentFrames() {
        val model = GmmVoiceTrainer.train(
            listOf(
                featureFrames(center = 0.4f, wobble = 0.04f),
                featureFrames(center = 0.43f, wobble = 0.04f),
                featureFrames(center = 0.38f, wobble = 0.05f)
            )
        )

        assertNotNull(model)

        val similarScore = GmmVoiceTrainer.confidence(
            model!!,
            featureFrames(center = 0.41f, wobble = 0.04f)
        )
        val differentScore = GmmVoiceTrainer.confidence(
            model,
            featureFrames(center = -0.45f, wobble = 0.11f)
        )

        assertTrue(similarScore > 0.75f)
        assertTrue(differentScore < similarScore - 0.20f)
    }

    @Test
    fun trainerRejectsInsufficientFrameCount() {
        val model = GmmVoiceTrainer.train(
            listOf(listOf(FloatArray(FEATURE_DIMENSION) { 0.1f }))
        )

        assertTrue(model == null)
    }

    private fun featureFrames(center: Float, wobble: Float): List<FloatArray> {
        return List(80) { frame ->
            FloatArray(FEATURE_DIMENSION) { coefficient ->
                center + wobble * sin(frame * 0.17 + coefficient * 0.31).toFloat()
            }
        }
    }

    private companion object {
        const val FEATURE_DIMENSION = 13
    }
}
