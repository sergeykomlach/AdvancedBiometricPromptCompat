package dev.skomlach.biometric.compat.engine.internal.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class CepstralVoiceEngine : VoiceEngine {
    override fun isAvailable(): Boolean = true

    override fun extractEmbedding(sample: VoiceSample): VoiceEmbeddingResult? {
        val providedEmbedding = sample.embedding
        if (providedEmbedding != null) {
            return providedEmbedding.normalizedCopy()?.let { VoiceEmbeddingResult(it) }
        }

        val qualityIssue = sample.qualityIssue()
        if (qualityIssue != VoiceQualityIssue.NONE) {
            return VoiceEmbeddingResult(FloatArray(0), qualityIssue)
        }

        val pcm = sample.pcmFloat ?: return null
        val mfccFrames = normalizeFrames(extractMfccFrames(pcm, sample.sampleRateHz))
        if (mfccFrames.size < MIN_FRAMES) return VoiceEmbeddingResult(FloatArray(0), VoiceQualityIssue.SAMPLE_TOO_SHORT)

        val embedding = pooledEmbedding(mfccFrames)
        return embedding.normalizedCopy()?.let { VoiceEmbeddingResult(it, featureFrames = mfccFrames) }
    }

    private fun extractMfccFrames(pcm: FloatArray, sampleRateHz: Int): List<FloatArray> {
        val frameLength = (sampleRateHz * FRAME_MS / 1000.0).roundToInt().coerceAtLeast(MIN_FRAME_LENGTH)
        val hopLength = (sampleRateHz * HOP_MS / 1000.0).roundToInt().coerceAtLeast(1)
        val fftSize = nextPowerOfTwo(frameLength)
        val hamming = FloatArray(frameLength) { index ->
            (0.54 - 0.46 * cos(2.0 * PI * index / (frameLength - 1).coerceAtLeast(1))).toFloat()
        }
        val filters = melFilters(sampleRateHz, fftSize)
        val result = ArrayList<FloatArray>()
        var offset = 0
        var previous = 0f
        while (offset + frameLength <= pcm.size) {
            val real = DoubleArray(fftSize)
            val imaginary = DoubleArray(fftSize)
            var frameEnergy = 0.0
            for (index in 0 until frameLength) {
                val raw = pcm[offset + index].coerceIn(-1f, 1f)
                frameEnergy += raw * raw
                val emphasized = (raw - PRE_EMPHASIS * previous).toDouble()
                previous = raw
                real[index] = emphasized * hamming[index]
            }
            if (sqrt(frameEnergy / frameLength) < MIN_FRAME_RMS) {
                offset += hopLength
                continue
            }
            fft(real, imaginary)
            val spectrum = powerSpectrum(real, imaginary)
            val logMel = FloatArray(MEL_FILTERS)
            for (filterIndex in 0 until MEL_FILTERS) {
                var energy = 0.0
                val filter = filters[filterIndex]
                for (bin in filter.indices) {
                    energy += spectrum[bin] * filter[bin]
                }
                logMel[filterIndex] = ln(energy.coerceAtLeast(MIN_ENERGY)).toFloat()
            }
            result.add(dct(logMel, MFCC_COUNT))
            offset += hopLength
        }
        return result
    }

    private fun normalizeFrames(frames: List<FloatArray>): List<FloatArray> {
        if (frames.isEmpty()) return emptyList()
        val mean = FloatArray(MFCC_COUNT)
        frames.forEach { frame ->
            for (coefficient in 0 until MFCC_COUNT) {
                mean[coefficient] += frame[coefficient] / frames.size
            }
        }
        val std = FloatArray(MFCC_COUNT)
        frames.forEach { frame ->
            for (coefficient in 0 until MFCC_COUNT) {
                val diff = frame[coefficient] - mean[coefficient]
                std[coefficient] += diff * diff / frames.size
            }
        }
        for (coefficient in 0 until MFCC_COUNT) {
            std[coefficient] = sqrt(std[coefficient]).coerceAtLeast(MIN_CEPSTRAL_STD)
        }
        return frames.map { frame ->
            FloatArray(MFCC_COUNT) { coefficient ->
                ((frame[coefficient] - mean[coefficient]) / std[coefficient]).coerceIn(
                    -MAX_NORMALIZED_COEFFICIENT,
                    MAX_NORMALIZED_COEFFICIENT
                )
            }
        }
    }

    private fun pooledEmbedding(mfccFrames: List<FloatArray>): FloatArray {
        val deltas = deltaFrames(mfccFrames)
        val deltaDeltas = deltaFrames(deltas)
        return stats(mfccFrames) + stats(deltas) + stats(deltaDeltas)
    }

    private fun stats(frames: List<FloatArray>): FloatArray {
        val result = FloatArray(MFCC_COUNT * 2)
        for (coefficient in 0 until MFCC_COUNT) {
            var sum = 0.0
            frames.forEach { sum += it[coefficient] }
            val mean = sum / frames.size
            var variance = 0.0
            frames.forEach {
                val diff = it[coefficient] - mean
                variance += diff * diff
            }
            result[coefficient] = mean.toFloat()
            result[MFCC_COUNT + coefficient] = sqrt(variance / frames.size).toFloat()
        }
        return result
    }

    private fun deltaFrames(frames: List<FloatArray>): List<FloatArray> {
        return frames.indices.map { index ->
            val previous = frames[(index - 1).coerceAtLeast(0)]
            val next = frames[(index + 1).coerceAtMost(frames.lastIndex)]
            FloatArray(MFCC_COUNT) { coefficient -> (next[coefficient] - previous[coefficient]) * 0.5f }
        }
    }

    private fun melFilters(sampleRateHz: Int, fftSize: Int): Array<FloatArray> {
        val binCount = fftSize / 2 + 1
        val lowMel = hzToMel(LOW_FREQ_HZ)
        val highMel = hzToMel(sampleRateHz / 2.0)
        val melPoints = DoubleArray(MEL_FILTERS + 2) { index ->
            lowMel + (highMel - lowMel) * index / (MEL_FILTERS + 1)
        }
        val bins = melPoints.map { mel ->
            ((fftSize + 1) * melToHz(mel) / sampleRateHz).roundToInt().coerceIn(0, binCount - 1)
        }
        return Array(MEL_FILTERS) { filterIndex ->
            val filter = FloatArray(binCount)
            val left = bins[filterIndex]
            val center = bins[filterIndex + 1].coerceAtLeast(left + 1)
            val right = bins[filterIndex + 2].coerceAtLeast(center + 1)
            for (bin in left until center.coerceAtMost(binCount)) {
                filter[bin] = ((bin - left).toFloat() / (center - left).coerceAtLeast(1))
            }
            for (bin in center until right.coerceAtMost(binCount)) {
                filter[bin] = ((right - bin).toFloat() / (right - center).coerceAtLeast(1))
            }
            filter
        }
    }

    private fun powerSpectrum(real: DoubleArray, imaginary: DoubleArray): DoubleArray {
        val result = DoubleArray(real.size / 2 + 1)
        for (index in result.indices) {
            result[index] = (real[index] * real[index] + imaginary[index] * imaginary[index]) / real.size
        }
        return result
    }

    private fun dct(values: FloatArray, coefficientCount: Int): FloatArray {
        return FloatArray(coefficientCount) { coefficient ->
            var sum = 0.0
            for (index in values.indices) {
                sum += values[index] * cos(PI * coefficient * (index + 0.5) / values.size)
            }
            sum.toFloat()
        }
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val size = real.size
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realTemp = real[i]
                real[i] = real[j]
                real[j] = realTemp
                val imaginaryTemp = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryTemp
            }
        }

        var length = 2
        while (length <= size) {
            val angle = -2.0 * PI / length
            val wLengthReal = cos(angle)
            val wLengthImaginary = sin(angle)
            var i = 0
            while (i < size) {
                var wReal = 1.0
                var wImaginary = 0.0
                for (k in 0 until length / 2) {
                    val evenIndex = i + k
                    val oddIndex = evenIndex + length / 2
                    val oddReal = real[oddIndex] * wReal - imaginary[oddIndex] * wImaginary
                    val oddImaginary = real[oddIndex] * wImaginary + imaginary[oddIndex] * wReal
                    real[oddIndex] = real[evenIndex] - oddReal
                    imaginary[oddIndex] = imaginary[evenIndex] - oddImaginary
                    real[evenIndex] += oddReal
                    imaginary[evenIndex] += oddImaginary
                    val nextWReal = wReal * wLengthReal - wImaginary * wLengthImaginary
                    wImaginary = wReal * wLengthImaginary + wImaginary * wLengthReal
                    wReal = nextWReal
                }
                i += length
            }
            length = length shl 1
        }
    }

    private fun FloatArray.normalizedCopy(): FloatArray? {
        if (!isValidEmbedding()) return null
        var sumSquares = 0.0
        for (value in this) {
            sumSquares += value * value
        }
        val norm = sqrt(sumSquares).toFloat()
        if (norm <= 0f || !norm.isFinite()) return null
        return FloatArray(size) { index -> this[index] / norm }
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var result = 1
        while (result < value) result = result shl 1
        return result
    }

    private fun hzToMel(hz: Double): Double = 2595.0 * kotlin.math.log10(1.0 + hz / 700.0)

    private fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

    private companion object {
        const val FRAME_MS = 25
        const val HOP_MS = 10
        const val MIN_FRAME_LENGTH = 128
        const val MIN_FRAMES = 8
        const val MIN_FRAME_RMS = 0.006f
        const val PRE_EMPHASIS = 0.97f
        const val LOW_FREQ_HZ = 40.0
        const val MEL_FILTERS = 26
        const val MFCC_COUNT = 13
        const val MIN_CEPSTRAL_STD = 1.0E-3f
        const val MAX_NORMALIZED_COEFFICIENT = 8f
        const val MIN_ENERGY = 1.0E-12
    }
}
