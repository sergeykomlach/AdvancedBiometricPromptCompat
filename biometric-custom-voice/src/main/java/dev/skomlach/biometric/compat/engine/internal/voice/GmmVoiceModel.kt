package dev.skomlach.biometric.compat.engine.internal.voice

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

data class GmmVoiceModel(
    val weights: FloatArray,
    val means: List<FloatArray>,
    val variances: List<FloatArray>,
    val enrollmentLogLikelihood: Float,
    val enrollmentLogLikelihoodStd: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GmmVoiceModel
        return weights.contentEquals(other.weights) &&
            means.contentDeepEquals(other.means) &&
            variances.contentDeepEquals(other.variances) &&
            enrollmentLogLikelihood == other.enrollmentLogLikelihood &&
            enrollmentLogLikelihoodStd == other.enrollmentLogLikelihoodStd
    }

    override fun hashCode(): Int {
        var result = weights.contentHashCode()
        result = 31 * result + means.contentDeepHashCode()
        result = 31 * result + variances.contentDeepHashCode()
        result = 31 * result + enrollmentLogLikelihood.hashCode()
        result = 31 * result + enrollmentLogLikelihoodStd.hashCode()
        return result
    }
}

object GmmVoiceTrainer {
    fun train(featureBatches: List<List<FloatArray>>): GmmVoiceModel? {
        val frames = validFrames(featureBatches.flatten())
        if (frames.size < MIN_TRAINING_FRAMES) return null
        val dimension = frames.first().size
        val componentCount = min(MAX_COMPONENTS, (frames.size / FRAMES_PER_COMPONENT).coerceAtLeast(1))
        val means = initializeMeans(frames, componentCount)
        val variances = List(componentCount) { globalVariance(frames, dimension) }
        val weights = FloatArray(componentCount) { 1f / componentCount }
        repeat(EM_ITERATIONS) {
            val responsibilities = responsibilities(frames, weights, means, variances)
            updateWeights(weights, responsibilities)
            updateMeans(means, frames, responsibilities)
            updateVariances(variances, means, frames, responsibilities)
        }
        val likelihoods = frames.map { frame -> logLikelihood(frame, weights, means, variances) }
        val meanLikelihood = likelihoods.average().toFloat()
        val stdLikelihood = likelihoods
            .map { value -> (value - meanLikelihood) * (value - meanLikelihood) }
            .average()
            .takeIf { !it.isNaN() }
            ?.let { sqrt(it).toFloat() }
            ?: 0f
        return GmmVoiceModel(
            weights = weights,
            means = means.map { it.copyOf() },
            variances = variances.map { it.copyOf() },
            enrollmentLogLikelihood = meanLikelihood,
            enrollmentLogLikelihoodStd = stdLikelihood.coerceAtLeast(MIN_LIKELIHOOD_STD)
        )
    }

    fun confidence(model: GmmVoiceModel, probeFrames: List<FloatArray>): Float {
        return confidenceDetails(model, probeFrames).confidence
    }

    fun confidenceDetails(model: GmmVoiceModel, probeFrames: List<FloatArray>): GmmConfidenceDetails {
        val frames = validFrames(probeFrames)
        if (frames.size < MIN_PROBE_FRAMES || !model.isUsable()) {
            return GmmConfidenceDetails(
                confidence = 0f,
                averageLogLikelihood = Float.NEGATIVE_INFINITY,
                enrollmentLogLikelihood = model.enrollmentLogLikelihood,
                likelihoodDrop = Float.POSITIVE_INFINITY,
                allowedDrop = 0f,
                probeFrameCount = frames.size,
                componentCount = model.weights.size
            )
        }
        val likelihood = averageLogLikelihood(model, frames)
        val allowedDrop = (model.enrollmentLogLikelihoodStd * STD_MARGIN).coerceAtLeast(MIN_ALLOWED_DROP)
        val likelihoodDrop = model.enrollmentLogLikelihood - likelihood
        val confidence = (1f - (likelihoodDrop / allowedDrop))
            .coerceIn(0f, 1f)
        return GmmConfidenceDetails(
            confidence = confidence,
            averageLogLikelihood = likelihood,
            enrollmentLogLikelihood = model.enrollmentLogLikelihood,
            likelihoodDrop = likelihoodDrop,
            allowedDrop = allowedDrop,
            probeFrameCount = frames.size,
            componentCount = model.weights.size
        )
    }

    fun averageLogLikelihood(model: GmmVoiceModel, probeFrames: List<FloatArray>): Float {
        val frames = validFrames(probeFrames)
        if (frames.isEmpty() || !model.isUsable()) return Float.NEGATIVE_INFINITY
        return frames
            .map { frame -> logLikelihood(frame, model.weights, model.means, model.variances) }
            .average()
            .toFloat()
    }

    private fun responsibilities(
        frames: List<FloatArray>,
        weights: FloatArray,
        means: List<FloatArray>,
        variances: List<FloatArray>
    ): List<FloatArray> {
        return frames.map { frame ->
            val logProbabilities = FloatArray(weights.size) { component ->
                ln(weights[component].coerceAtLeast(MIN_WEIGHT)) +
                    gaussianLogDensity(frame, means[component], variances[component])
            }
            val total = logSumExp(logProbabilities)
            FloatArray(weights.size) { component ->
                exp((logProbabilities[component] - total).toDouble()).toFloat()
            }
        }
    }

    private fun updateWeights(weights: FloatArray, responsibilities: List<FloatArray>) {
        for (component in weights.indices) {
            weights[component] = responsibilities.sumOf { it[component].toDouble() }
                .div(responsibilities.size)
                .toFloat()
                .coerceAtLeast(MIN_WEIGHT)
        }
        val sum = weights.sum().coerceAtLeast(MIN_WEIGHT)
        for (component in weights.indices) {
            weights[component] /= sum
        }
    }

    private fun updateMeans(
        means: List<FloatArray>,
        frames: List<FloatArray>,
        responsibilities: List<FloatArray>
    ) {
        for (component in means.indices) {
            val weight = responsibilities.sumOf { it[component].toDouble() }.coerceAtLeast(MIN_WEIGHT.toDouble())
            for (dimension in means[component].indices) {
                means[component][dimension] = frames.indices.sumOf { index ->
                    responsibilities[index][component].toDouble() * frames[index][dimension]
                }.div(weight).toFloat()
            }
        }
    }

    private fun updateVariances(
        variances: List<FloatArray>,
        means: List<FloatArray>,
        frames: List<FloatArray>,
        responsibilities: List<FloatArray>
    ) {
        for (component in variances.indices) {
            val weight = responsibilities.sumOf { it[component].toDouble() }.coerceAtLeast(MIN_WEIGHT.toDouble())
            for (dimension in variances[component].indices) {
                variances[component][dimension] = frames.indices.sumOf { index ->
                    val diff = frames[index][dimension] - means[component][dimension]
                    responsibilities[index][component].toDouble() * diff * diff
                }.div(weight).toFloat().coerceAtLeast(MIN_VARIANCE)
            }
        }
    }

    private fun initializeMeans(frames: List<FloatArray>, componentCount: Int): List<FloatArray> {
        val means = mutableListOf(frames.first().copyOf())
        while (means.size < componentCount) {
            val next = frames.maxBy { frame ->
                means.minOf { mean -> squaredDistance(frame, mean) }
            }
            means.add(next.copyOf())
        }
        return means
    }

    private fun globalVariance(frames: List<FloatArray>, dimension: Int): FloatArray {
        return FloatArray(dimension) { index ->
            val mean = frames.map { it[index] }.average()
            frames.map {
                val diff = it[index] - mean
                diff * diff
            }.average().toFloat().coerceAtLeast(MIN_VARIANCE)
        }
    }

    private fun logLikelihood(
        frame: FloatArray,
        weights: FloatArray,
        means: List<FloatArray>,
        variances: List<FloatArray>
    ): Float {
        val logProbabilities = FloatArray(weights.size) { component ->
            ln(weights[component].coerceAtLeast(MIN_WEIGHT)) +
                gaussianLogDensity(frame, means[component], variances[component])
        }
        return logSumExp(logProbabilities)
    }

    private fun gaussianLogDensity(frame: FloatArray, mean: FloatArray, variance: FloatArray): Float {
        val size = minOf(frame.size, mean.size, variance.size)
        var sum = 0.0
        for (index in 0 until size) {
            val varValue = variance[index].coerceAtLeast(MIN_VARIANCE)
            val diff = frame[index] - mean[index]
            sum += ln(2.0 * PI * varValue) + diff * diff / varValue
        }
        return (-0.5 * sum).toFloat()
    }

    private fun logSumExp(values: FloatArray): Float {
        val max = values.maxOrNull() ?: return Float.NEGATIVE_INFINITY
        var sum = 0.0
        for (value in values) {
            sum += exp((value - max).toDouble())
        }
        return (max + ln(sum)).toFloat()
    }

    private fun squaredDistance(first: FloatArray, second: FloatArray): Float {
        val size = minOf(first.size, second.size)
        var result = 0f
        for (index in 0 until size) {
            val diff = first[index] - second[index]
            result += diff * diff
        }
        return result
    }

    private fun validFrames(frames: List<FloatArray>): List<FloatArray> {
        val expectedSize = frames.firstOrNull()?.size ?: return emptyList()
        return frames.asSequence()
            .filter { frame ->
                frame.size == expectedSize && frame.all { it.isFinite() }
            }
            .map { it.copyOf() }
            .toList()
    }

    private fun GmmVoiceModel.isUsable(): Boolean {
        return weights.isNotEmpty() &&
            weights.size == means.size &&
            means.size == variances.size &&
            means.isNotEmpty() &&
            means.all { it.isNotEmpty() } &&
            variances.all { it.size == means.first().size }
    }

    private const val MAX_COMPONENTS = 4
    private const val FRAMES_PER_COMPONENT = 35
    private const val EM_ITERATIONS = 12
    private const val MIN_TRAINING_FRAMES = 24
    private const val MIN_PROBE_FRAMES = 8
    private const val MIN_VARIANCE = 1.0E-4f
    private const val MIN_WEIGHT = 1.0E-6f
    private const val MIN_LIKELIHOOD_STD = 0.5f
    private const val MIN_ALLOWED_DROP = 8f
    private const val STD_MARGIN = 4f
}

data class GmmConfidenceDetails(
    val confidence: Float,
    val averageLogLikelihood: Float,
    val enrollmentLogLikelihood: Float,
    val likelihoodDrop: Float,
    val allowedDrop: Float,
    val probeFrameCount: Int,
    val componentCount: Int
)
