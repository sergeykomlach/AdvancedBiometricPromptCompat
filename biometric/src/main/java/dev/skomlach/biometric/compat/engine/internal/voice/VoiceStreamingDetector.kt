package dev.skomlach.biometric.compat.engine.internal.voice

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class VoiceStreamingDetection(
    val detectedSpeech: Boolean,
    val isComplete: Boolean,
    val completedSample: FloatArray?,
    val activeSample: FloatArray?
)

class VoiceStreamingDetector(
    private val sampleRateHz: Int,
    private val minVoiceRms: Float = 0.012f,
    private val noiseMultiplier: Float = 2.5f,
    private val speechStartFrames: Int = 3,
    private val shortPauseFrames: Int = 4,
    private val endSilenceFrames: Int = 12,
    private val minimumSampleMs: Long = 900L
) {
    private val conditioner = VoiceSignalConditioner()

    fun detect(chunks: List<FloatArray>): VoiceStreamingDetection {
        if (chunks.isEmpty() || sampleRateHz <= 0) {
            return VoiceStreamingDetection(
                detectedSpeech = false,
                isComplete = false,
                completedSample = null,
                activeSample = null
            )
        }

        val conditionedChunks = chunks.map { chunk -> conditioner.condition(chunk) }
        val inputRmsValues = conditionedChunks.map { chunk -> chunk.inputRms }
        val conditionedRmsValues = conditionedChunks.map { chunk -> chunk.conditionedRms }
        val sorted = inputRmsValues.sorted()
        val quietFrameCount = max(1, sorted.size / 5)
        val noiseFloor = sorted.take(quietFrameCount).average().toFloat()
        val voicedThreshold = max(minVoiceRms, noiseFloor * noiseMultiplier)
        val speechEvidenceThreshold = max(
            conditioner.silenceRmsThreshold,
            noiseFloor * SPEECH_EVIDENCE_MULTIPLIER
        )
        val voiced = conditionedChunks.mapIndexed { index, chunk ->
            chunk.inputRms >= speechEvidenceThreshold &&
                conditionedRmsValues[index] >= voicedThreshold
        }

        val speechStart = firstSpeechStart(voiced)
            ?: return VoiceStreamingDetection(
                detectedSpeech = false,
                isComplete = false,
                completedSample = null,
                activeSample = null
            )

        var lastVoicedIndex = speechStart
        var currentSilenceRun = 0
        for (index in speechStart until voiced.size) {
            if (voiced[index]) {
                lastVoicedIndex = index
                currentSilenceRun = 0
                continue
            }
            currentSilenceRun += 1
            if (currentSilenceRun >= endSilenceFrames) {
                val completed = flattenChunks(conditionedChunks.map { it.samples }, speechStart, lastVoicedIndex + 1)
                return VoiceStreamingDetection(
                    detectedSpeech = true,
                    isComplete = completed.size >= minimumSampleCount(),
                    completedSample = completed.takeIf { it.size >= minimumSampleCount() },
                    activeSample = completed
                )
            }
            if (currentSilenceRun > shortPauseFrames) {
                continue
            }
        }

        val activeSample = flattenChunks(conditionedChunks.map { it.samples }, speechStart, lastVoicedIndex + 1)
        return VoiceStreamingDetection(
            detectedSpeech = true,
            isComplete = false,
            completedSample = null,
            activeSample = activeSample
        )
    }

    private fun firstSpeechStart(voiced: List<Boolean>): Int? {
        var index = 0
        while (index < voiced.size) {
            if (!voiced[index]) {
                index += 1
                continue
            }

            val candidateStart = index
            val windowEnd = min(voiced.size, candidateStart + speechStartWindowFrames())
            var voicedCount = 0
            var longestSilenceRun = 0
            var currentSilenceRun = 0
            for (cursor in candidateStart until windowEnd) {
                if (voiced[cursor]) {
                    voicedCount += 1
                    currentSilenceRun = 0
                } else {
                    currentSilenceRun += 1
                    longestSilenceRun = max(longestSilenceRun, currentSilenceRun)
                }
            }
            if (voicedCount >= stableSpeechFrames() && longestSilenceRun <= maxStartGapFrames()) {
                return candidateStart
            }

            while (index < voiced.size && voiced[index]) {
                index += 1
            }
        }
        return null
    }

    private fun flattenChunks(chunks: List<FloatArray>, startIndex: Int, endExclusive: Int): FloatArray {
        val length = (startIndex until endExclusive).sumOf { index -> chunks[index].size }
        val flattened = FloatArray(length)
        var offset = 0
        for (index in startIndex until endExclusive) {
            val chunk = chunks[index]
            chunk.copyInto(flattened, destinationOffset = offset)
            offset += chunk.size
        }
        return flattened
    }

    private fun minimumSampleCount(): Int {
        return ((sampleRateHz * minimumSampleMs) / 1000L).toInt()
    }

    private fun stableSpeechFrames(): Int {
        return speechStartFrames + 2
    }

    private fun speechStartWindowFrames(): Int {
        return stableSpeechFrames() + 2
    }

    private fun maxStartGapFrames(): Int {
        return max(1, shortPauseFrames / 3)
    }

    private companion object {
        const val SPEECH_EVIDENCE_MULTIPLIER = 1.4f
    }
}
