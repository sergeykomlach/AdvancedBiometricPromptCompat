package dev.skomlach.biometric.compat.engine.internal.voice

import kotlin.math.max
import kotlin.math.min

internal data class VoiceStreamingDetection(
    val detectedSpeech: Boolean,
    val isComplete: Boolean,
    val completedSample: FloatArray?,
    val activeSample: FloatArray?
)

internal class VoiceStreamingDetector(
    private val sampleRateHz: Int,
    private val minVoiceRms: Float = 0.012f,
    private val noiseMultiplier: Float = 2.5f,
    private val speechStartFrames: Int = 3,
    private val shortPauseFrames: Int = 4,
    private val endSilenceFrames: Int = 12,
    private val minimumSampleMs: Long = 900L
) {
    private val conditioner = VoiceSignalConditioner()
    private val cachedChunks = ArrayList<FloatArray>()
    private val cachedInputRmsValues = ArrayList<Float>()
    private val cachedConditionedRmsValues = ArrayList<Float>()
    private val cachedChunkEndOffsets = ArrayList<Int>()
    private val sortedInputRmsValues = ArrayList<Float>()
    private val sortedConditionedRmsValues = ArrayList<Float>()
    private var cachedSampleStart = -1
    private var cachedSampleEndExclusive = -1
    private var cachedSample: FloatArray? = null

    fun detect(chunks: List<FloatArray>): VoiceStreamingDetection {
        if (chunks.isEmpty() || sampleRateHz <= 0) {
            return VoiceStreamingDetection(
                detectedSpeech = false,
                isComplete = false,
                completedSample = null,
                activeSample = null
            )
        }

        syncChunkAnalysis(chunks)

        val quietFrameCount = max(1, sortedInputRmsValues.size / 5)
        val rawNoiseFloor = averageQuietRms(sortedInputRmsValues, quietFrameCount)
        val conditionedNoiseFloor = averageQuietRms(sortedConditionedRmsValues, quietFrameCount)
        val hasReliableQuietFloor = rawNoiseFloor <= conditioner.silenceRmsThreshold * RELIABLE_QUIET_FLOOR_MULTIPLIER
        val voicedThreshold = if (hasReliableQuietFloor) {
            max(minVoiceRms, conditionedNoiseFloor * noiseMultiplier)
        } else {
            minVoiceRms
        }
        val speechEvidenceThreshold = max(
            conditioner.silenceRmsThreshold,
            if (hasReliableQuietFloor) rawNoiseFloor * SPEECH_EVIDENCE_MULTIPLIER else 0f
        )
        val voiced = BooleanArray(chunks.size) { index ->
            cachedInputRmsValues[index] >= speechEvidenceThreshold &&
                cachedConditionedRmsValues[index] >= voicedThreshold
        }

        val speechWindow = detectSpeechWindow(voiced)
            ?: return VoiceStreamingDetection(
                detectedSpeech = false,
                isComplete = false,
                completedSample = null,
                activeSample = null
            )

        val activeSample = sampleSlice(chunks, speechWindow)
        if (speechWindow.isComplete) {
            return VoiceStreamingDetection(
                detectedSpeech = true,
                isComplete = true,
                completedSample = activeSample,
                activeSample = activeSample
            )
        }

        return VoiceStreamingDetection(
            detectedSpeech = true,
            isComplete = false,
            completedSample = null,
            activeSample = activeSample
        )
    }

    private fun detectSpeechWindow(voiced: BooleanArray): SpeechWindow? {
        val initialSpeechStart = firstSpeechStart(voiced) ?: return null
        var activeSpeechStart = initialSpeechStart
        var lastVoicedIndex = initialSpeechStart
        var currentSilenceRun = 0
        var restartVoicedRun = 0
        var restartCandidateStart = -1
        var restartLastVoicedIndex = -1

        for (index in initialSpeechStart until voiced.size) {
            if (voiced[index]) {
                if (currentSilenceRun > shortPauseFrames) {
                    if (restartVoicedRun == 0) {
                        restartCandidateStart = index
                    }
                    restartVoicedRun += 1
                    restartLastVoicedIndex = index
                    if (restartVoicedRun >= speechStartFrames) {
                        activeSpeechStart = restartCandidateStart
                        lastVoicedIndex = restartLastVoicedIndex
                        currentSilenceRun = 0
                        restartVoicedRun = 0
                        restartCandidateStart = -1
                        restartLastVoicedIndex = -1
                    }
                    continue
                }
                lastVoicedIndex = index
                currentSilenceRun = 0
                restartVoicedRun = 0
                restartCandidateStart = -1
                restartLastVoicedIndex = -1
                continue
            }

            currentSilenceRun += 1
            restartVoicedRun = 0
            restartCandidateStart = -1
            restartLastVoicedIndex = -1
            if (currentSilenceRun >= endSilenceFrames) {
                return SpeechWindow(
                    startIndex = activeSpeechStart,
                    endExclusive = lastVoicedIndex + 1,
                    isComplete = true
                )
            }
        }

        if (currentSilenceRun > shortPauseFrames) {
            if (restartCandidateStart >= 0 && restartLastVoicedIndex >= restartCandidateStart) {
                return SpeechWindow(
                    startIndex = restartCandidateStart,
                    endExclusive = restartLastVoicedIndex + 1,
                    isComplete = false
                )
            }
            return SpeechWindow(
                startIndex = -1,
                endExclusive = -1,
                isComplete = false
            )
        }

        return SpeechWindow(
            startIndex = activeSpeechStart,
            endExclusive = lastVoicedIndex + 1,
            isComplete = false
        )
    }

    private fun firstSpeechStart(voiced: BooleanArray): Int? {
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

    private fun syncChunkAnalysis(chunks: List<FloatArray>) {
        if (!sharesCachedPrefix(chunks)) {
            clearAnalysisCache()
        }
        for (index in cachedChunks.size until chunks.size) {
            val chunk = chunks[index]
            val conditioned = conditioner.condition(chunk)
            cachedChunks += chunk
            cachedInputRmsValues += conditioned.inputRms
            cachedConditionedRmsValues += conditioned.conditionedRms
            cachedChunkEndOffsets += (cachedChunkEndOffsets.lastOrNull() ?: 0) + chunk.size
            insertSortedInputRms(conditioned.inputRms)
            insertSortedConditionedRms(conditioned.conditionedRms)
        }
    }

    private fun sharesCachedPrefix(chunks: List<FloatArray>): Boolean {
        if (chunks.size < cachedChunks.size) return false
        for (index in cachedChunks.indices) {
            if (cachedChunks[index] !== chunks[index]) {
                return false
            }
        }
        return true
    }

    private fun clearAnalysisCache() {
        cachedChunks.clear()
        cachedInputRmsValues.clear()
        cachedConditionedRmsValues.clear()
        cachedChunkEndOffsets.clear()
        sortedInputRmsValues.clear()
        sortedConditionedRmsValues.clear()
        cachedSampleStart = -1
        cachedSampleEndExclusive = -1
        cachedSample = null
    }

    private fun insertSortedInputRms(value: Float) {
        val index = sortedInputRmsValues.binarySearch(value)
        val insertionPoint = if (index >= 0) index else -index - 1
        sortedInputRmsValues.add(insertionPoint, value)
    }

    private fun insertSortedConditionedRms(value: Float) {
        val index = sortedConditionedRmsValues.binarySearch(value)
        val insertionPoint = if (index >= 0) index else -index - 1
        sortedConditionedRmsValues.add(insertionPoint, value)
    }

    private fun averageQuietRms(sortedValues: List<Float>, quietFrameCount: Int): Float {
        var sum = 0.0
        for (index in 0 until quietFrameCount) {
            sum += sortedValues[index]
        }
        return (sum / quietFrameCount).toFloat()
    }

    private fun sampleSlice(chunks: List<FloatArray>, speechWindow: SpeechWindow): FloatArray? {
        if (speechWindow.startIndex < 0 || speechWindow.endExclusive <= speechWindow.startIndex) {
            cachedSampleStart = -1
            cachedSampleEndExclusive = -1
            cachedSample = null
            return null
        }
        cachedSample?.let { sample ->
            if (cachedSampleStart == speechWindow.startIndex && cachedSampleEndExclusive == speechWindow.endExclusive) {
                return sample
            }
        }
        val flattened = flattenChunks(chunks, speechWindow.startIndex, speechWindow.endExclusive)
        cachedSampleStart = speechWindow.startIndex
        cachedSampleEndExclusive = speechWindow.endExclusive
        cachedSample = flattened
        return flattened
    }

    private fun flattenChunks(chunks: List<FloatArray>, startIndex: Int, endExclusive: Int): FloatArray {
        val prefixEnd = cachedChunkEndOffsets[endExclusive - 1]
        val prefixStart = if (startIndex > 0) cachedChunkEndOffsets[startIndex - 1] else 0
        val length = prefixEnd - prefixStart
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

    private data class SpeechWindow(
        val startIndex: Int,
        val endExclusive: Int,
        val isComplete: Boolean
    )

    private companion object {
        const val SPEECH_EVIDENCE_MULTIPLIER = 1.4f
        const val RELIABLE_QUIET_FLOOR_MULTIPLIER = 1.5f
    }
}
