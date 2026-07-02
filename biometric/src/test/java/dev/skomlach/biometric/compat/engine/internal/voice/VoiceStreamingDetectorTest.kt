package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoiceStreamingDetectorTest {
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
    fun detectKeepsAllowedShortPauseInSameUtteranceButRestartsAfterLongerPause() {
        val shortPauseDetector = VoiceStreamingDetector(
            sampleRateHz = SAMPLE_RATE,
            shortPauseFrames = 4
        )
        val firstVoice = voiceChunk(frequencyHz = 180.0, amplitude = 0.08f)
        val secondVoice = voiceChunk(frequencyHz = 220.0, amplitude = 0.08f)

        val allowedPause = shortPauseDetector.detect(
            buildList {
                repeat(6) { add(silenceChunk()) }
                repeat(10) { add(firstVoice.copyOf()) }
                repeat(4) { add(silenceChunk(scale = 0.003f)) }
                repeat(10) { add(secondVoice.copyOf()) }
            }
        )

        assertTrue(allowedPause.detectedSpeech)
        assertFalse(allowedPause.isComplete)
        assertNotNull(allowedPause.activeSample)
        assertEquals(CHUNK_SIZE * 20, allowedPause.activeSample!!.size)

        val longerPause = shortPauseDetector.detect(
            buildList {
                repeat(6) { add(silenceChunk()) }
                repeat(10) { add(firstVoice.copyOf()) }
                repeat(5) { add(silenceChunk(scale = 0.003f)) }
                repeat(10) { add(secondVoice.copyOf()) }
            }
        )

        assertTrue(longerPause.detectedSpeech)
        assertFalse(longerPause.isComplete)
        assertNotNull(longerPause.activeSample)
        val longerPauseSample = longerPause.activeSample!!
        assertEquals(CHUNK_SIZE * 10, longerPauseSample.size)
        assertChunkEquals(secondVoice, longerPauseSample, destinationOffset = 0)
    }

    @Test
    fun detectRejectsSilenceOnlyChunks() {
        val detector = VoiceStreamingDetector(sampleRateHz = SAMPLE_RATE)

        val result = detector.detect(List(32) { silenceChunk() })

        assertFalse(result.detectedSpeech)
        assertFalse(result.isComplete)
        assertTrue(result.completedSample == null)
    }

    @Test
    fun detectPreservesRawPayloadSamplesWhileScoringConditionedChunks() {
        val detector = VoiceStreamingDetector(sampleRateHz = SAMPLE_RATE)
        val firstVoice = voiceChunk(frequencyHz = 180.0, amplitude = 0.08f, dcOffset = 0.12f)
        val secondVoice = voiceChunk(frequencyHz = 220.0, amplitude = 0.09f, dcOffset = 0.12f)

        val result = detector.detect(
            buildList {
                repeat(6) { add(silenceChunk()) }
                repeat(24) { add(firstVoice.copyOf()) }
                repeat(4) { add(silenceChunk(scale = 0.003f)) }
                repeat(24) { add(secondVoice.copyOf()) }
                repeat(14) { add(silenceChunk()) }
            }
        )

        assertTrue(result.detectedSpeech)
        assertTrue(result.isComplete)
        assertNotNull(result.completedSample)
        val payload = result.completedSample!!
        assertEquals(firstVoice.size * 48, payload.size)
        assertChunkEquals(firstVoice, payload, destinationOffset = 0)
        assertChunkEquals(secondVoice, payload, destinationOffset = firstVoice.size * 24)
    }

    private fun voiceChunk(
        frequencyHz: Double,
        amplitude: Float,
        dcOffset: Float = 0f
    ): FloatArray {
        return FloatArray(CHUNK_SIZE) { index ->
            val time = index.toDouble() / SAMPLE_RATE
            (dcOffset + amplitude * sin(2.0 * PI * frequencyHz * time)).toFloat()
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

    private fun assertChunkEquals(expected: FloatArray, actual: FloatArray, destinationOffset: Int) {
        expected.forEachIndexed { index, value ->
            assertEquals(value, actual[destinationOffset + index], FLOAT_DELTA)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_SIZE = 320
        const val MINIMUM_SAMPLE_COUNT = 14_400
        const val FLOAT_DELTA = 0.000001f
    }
}
