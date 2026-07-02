package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoiceStreamingDetectionTest {
    @Test
    fun detectIgnoresShortNoisyWarmupBeforeStableSpeechStart() {
        val detector = VoiceStreamingDetector(sampleRateHz = SAMPLE_RATE)

        val result = detector.detect(
            buildList {
                repeat(3) { add(noiseChunk(amplitude = 0.06f)) }
                repeat(2) { add(silenceChunk()) }
                repeat(30) { add(voiceChunk(180.0, amplitude = 0.07f)) }
            }
        )

        assertTrue(result.detectedSpeech)
        assertFalse(result.isComplete)
        assertNotNull(result.activeSample)
        assertTrue(result.activeSample!!.size in (CHUNK_SIZE * 28)..(CHUNK_SIZE * 31))
    }

    @Test
    fun detectWaitsThroughShortPauseAndCompletesAfterSustainedSilence() {
        val detector = VoiceStreamingDetector(sampleRateHz = SAMPLE_RATE)

        val partial = detector.detect(
            buildList {
                repeat(6) { add(silenceChunk()) }
                repeat(24) { add(voiceChunk(180.0, amplitude = 0.08f)) }
                repeat(4) { add(silenceChunk(scale = 0.003f)) }
            }
        )
        assertTrue(partial.detectedSpeech)
        assertFalse(partial.isComplete)

        val completed = detector.detect(
            buildList {
                repeat(6) { add(silenceChunk()) }
                repeat(24) { add(voiceChunk(180.0, amplitude = 0.08f)) }
                repeat(4) { add(silenceChunk(scale = 0.003f)) }
                repeat(24) { add(voiceChunk(220.0, amplitude = 0.08f)) }
                repeat(14) { add(silenceChunk()) }
            }
        )

        assertTrue(completed.detectedSpeech)
        assertTrue(completed.isComplete)
        assertNotNull(completed.completedSample)
        assertTrue(completed.completedSample!!.size >= MINIMUM_SAMPLE_COUNT)
    }

    @Test
    fun detectRejectsSilenceOnlyChunks() {
        val detector = VoiceStreamingDetector(sampleRateHz = SAMPLE_RATE)

        val result = detector.detect(List(32) { silenceChunk() })

        assertFalse(result.detectedSpeech)
        assertFalse(result.isComplete)
        assertTrue(result.completedSample == null)
    }

    private fun voiceChunk(frequencyHz: Double, amplitude: Float): FloatArray {
        return FloatArray(CHUNK_SIZE) { index ->
            val time = index.toDouble() / SAMPLE_RATE
            (amplitude * sin(2.0 * PI * frequencyHz * time)).toFloat()
        }
    }

    private fun silenceChunk(scale: Float = 0f): FloatArray {
        return FloatArray(CHUNK_SIZE) { index ->
            (scale * sin(index * 0.13)).toFloat()
        }
    }

    private fun noiseChunk(amplitude: Float): FloatArray {
        return FloatArray(CHUNK_SIZE) { index ->
            val signal = sin(index * 0.73) + 0.6 * sin(index * 1.91) + 0.4 * sin(index * 2.77)
            (amplitude * (signal / 2.0)).toFloat().coerceIn(-1f, 1f)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_SIZE = 320
        const val MINIMUM_SAMPLE_COUNT = 14_400
    }
}
