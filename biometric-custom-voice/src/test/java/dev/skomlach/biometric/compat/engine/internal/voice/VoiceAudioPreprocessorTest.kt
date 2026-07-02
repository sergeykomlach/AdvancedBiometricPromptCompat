package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoiceAudioPreprocessorTest {
    @Test
    fun preprocessTrimsLeadingAndTrailingSilenceWithoutDroppingVoice() {
        val voice = voiceLikeTone(durationSeconds = 2.0)
        val pcm = FloatArray(SAMPLE_RATE / 2) + voice + FloatArray(SAMPLE_RATE / 2)

        val result = VoiceAudioPreprocessor.preprocess(pcm, SAMPLE_RATE)

        assertEquals(VoiceQualityIssue.NONE, result.qualityIssue)
        assertTrue(result.pcm.size < pcm.size)
        assertTrue(result.pcm.size >= voice.size)
    }

    @Test
    fun qualityAcceptsLongCaptureWhenTrimmedVoiceIsUsable() {
        val voice = voiceLikeTone(durationSeconds = 2.0)
        val pcm = FloatArray(SAMPLE_RATE * 4) + voice + FloatArray(SAMPLE_RATE * 4)
        val sample = VoiceSample(SAMPLE_RATE, pcm, null, null)

        assertEquals(VoiceQualityIssue.NONE, sample.qualityIssue())
    }

    @Test
    fun preprocessRemovesDcOffsetFromVoiceSample() {
        val pcm = voiceLikeTone(durationSeconds = 1.5, dcOffset = 0.18f)

        val result = VoiceAudioPreprocessor.preprocess(pcm, SAMPLE_RATE)

        assertEquals(VoiceQualityIssue.NONE, result.qualityIssue)
        assertTrue(kotlin.math.abs(result.pcm.average()).toFloat() < 0.02f)
    }

    @Test
    fun preprocessRejectsNoiseOnlySample() {
        val pcm = FloatArray(SAMPLE_RATE * 2) { index ->
            (0.009f * sin(index * 0.71)).toFloat()
        }

        val result = VoiceAudioPreprocessor.preprocess(pcm, SAMPLE_RATE)

        assertEquals(VoiceQualityIssue.SAMPLE_TOO_QUIET, result.qualityIssue)
    }

    @Test
    fun preprocessRejectsClippingHeavySample() {
        val pcm = voiceLikeTone(durationSeconds = 1.5).also { sample ->
            for (index in sample.indices step 4) {
                sample[index] = 1f
            }
        }

        val result = VoiceAudioPreprocessor.preprocess(pcm, SAMPLE_RATE)

        assertEquals(VoiceQualityIssue.SAMPLE_CLIPPED, result.qualityIssue)
        assertTrue(result.metrics.clippedRatio > 0.08f)
    }

    @Test
    fun preprocessRejectsRepeatedLoopLikeSample() {
        val chunk = voiceLikeTone(durationSeconds = 0.2)
        val pcm = FloatArray(SAMPLE_RATE * 2) { index -> chunk[index % chunk.size] }

        val result = VoiceAudioPreprocessor.preprocess(pcm, SAMPLE_RATE)

        assertEquals(VoiceQualityIssue.SAMPLE_REPLAY_RISK, result.qualityIssue)
        assertTrue(result.metrics.repeatedChunkRatio > 0.60f)
    }

    @Test
    fun preprocessReportsSanitizedMetrics() {
        val pcm = FloatArray(SAMPLE_RATE / 2) + voiceLikeTone(durationSeconds = 1.5) + FloatArray(SAMPLE_RATE / 2)

        val result = VoiceAudioPreprocessor.preprocess(pcm, SAMPLE_RATE)

        assertEquals(VoiceQualityIssue.NONE, result.qualityIssue)
        assertEquals(2500L, result.metrics.rawDurationMs)
        assertTrue(result.metrics.voicedDurationMs >= 1500L)
        assertTrue(result.metrics.voicedFrameCount > 0)
    }

    @Test
    fun preprocessRejectsTooShortVoicedFragmentAfterTrim() {
        val shortVoice = voiceLikeTone(durationSeconds = 0.35)
        val pcm = FloatArray(SAMPLE_RATE) + shortVoice + FloatArray(SAMPLE_RATE)

        val result = VoiceAudioPreprocessor.preprocess(pcm, SAMPLE_RATE)

        assertEquals(VoiceQualityIssue.SAMPLE_TOO_SHORT, result.qualityIssue)
    }

    @Test
    fun preprocessNormalizesQuietVoicedSegmentWithoutIntroducingClipping() {
        val quietVoice = voiceLikeTone(durationSeconds = 1.5, amplitudeScale = 0.08f)
        val pcm = FloatArray(SAMPLE_RATE / 2) + quietVoice + FloatArray(SAMPLE_RATE / 2)
        val inputRms = rms(quietVoice)

        val result = VoiceAudioPreprocessor.preprocess(pcm, SAMPLE_RATE)
        val outputRms = rms(result.pcm)
        val clippedSamples = result.pcm.count { kotlin.math.abs(it) >= 0.98f }

        assertEquals(VoiceQualityIssue.NONE, result.qualityIssue)
        assertTrue(outputRms > inputRms)
        assertTrue(outputRms < 0.12f)
        assertEquals(0, clippedSamples)
    }

    private fun voiceLikeTone(
        durationSeconds: Double,
        dcOffset: Float = 0f,
        amplitudeScale: Float = 1f
    ): FloatArray {
        val size = (SAMPLE_RATE * durationSeconds).toInt()
        return FloatArray(size) { index ->
            val time = index.toDouble() / SAMPLE_RATE
            val harmonic = 0.32 * sin(2.0 * PI * 180.0 * time) +
                0.16 * sin(2.0 * PI * 360.0 * time) +
                0.07 * sin(2.0 * PI * 540.0 * time)
            ((harmonic + 0.01 * sin(index * 0.19)) * amplitudeScale + dcOffset)
                .toFloat()
                .coerceIn(-0.95f, 0.95f)
        }
    }

    private fun rms(pcm: FloatArray): Float {
        var sumSquares = 0.0
        for (sample in pcm) {
            sumSquares += sample * sample
        }
        return kotlin.math.sqrt(sumSquares / pcm.size.coerceAtLeast(1)).toFloat()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
