package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

class VoiceSignalConditionerTest {
    @Test
    fun conditionLiftsQuietVoiceTowardTargetRmsWithoutClipping() {
        val conditioner = VoiceSignalConditioner(
            targetRms = 0.08f,
            silenceRmsThreshold = 0.0025f,
            maxGain = 6f
        )

        val conditioned = conditioner.condition(
            voiceChunk(
                frequencyHz = 185.0,
                amplitude = 0.014f,
                dcOffset = 0.18f
            )
        )

        assertTrue(conditioned.inputRms < 0.02f)
        assertTrue(conditioned.conditionedRms > conditioned.inputRms * 3f)
        assertTrue(conditioned.conditionedRms in 0.055f..0.081f)
        assertTrue(abs(conditioned.samples.average().toFloat()) < 0.01f)
        assertTrue(maxAbs(conditioned.samples) < 1f)
    }

    @Test
    fun conditionDoesNotAmplifySilenceIntoPseudoSpeech() {
        val conditioner = VoiceSignalConditioner(
            targetRms = 0.08f,
            silenceRmsThreshold = 0.0025f,
            maxGain = 6f
        )

        val conditioned = conditioner.condition(
            silenceChunk(
                scale = 0.0008f,
                dcOffset = 0.04f
            )
        )

        assertTrue(conditioned.inputRms < 0.0025f)
        assertTrue(conditioned.conditionedRms < 0.0025f)
        assertTrue(abs(conditioned.samples.average().toFloat()) < 0.002f)
        assertTrue(maxAbs(conditioned.samples) < 0.01f)
    }

    private fun voiceChunk(
        frequencyHz: Double,
        amplitude: Float,
        dcOffset: Float
    ): FloatArray {
        return FloatArray(CHUNK_SIZE) { index ->
            val time = index.toDouble() / SAMPLE_RATE
            (dcOffset + amplitude * sin(2.0 * PI * frequencyHz * time)).toFloat()
        }
    }

    private fun silenceChunk(scale: Float, dcOffset: Float): FloatArray {
        return FloatArray(CHUNK_SIZE) { index ->
            (dcOffset + scale * sin(index * 0.13)).toFloat()
        }
    }

    private fun maxAbs(samples: FloatArray): Float {
        var maxValue = 0f
        for (sample in samples) {
            maxValue = max(maxValue, abs(sample))
        }
        return maxValue
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_SIZE = 320
    }
}
