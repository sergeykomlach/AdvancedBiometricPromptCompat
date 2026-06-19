package dev.skomlach.biometric.compat.engine.internal.voice

interface VoiceEngine {
    fun isAvailable(): Boolean
    fun extractEmbedding(sample: VoiceSample): VoiceEmbeddingResult?
}

data class VoiceEmbeddingResult(
    val embedding: FloatArray,
    val qualityIssue: VoiceQualityIssue = VoiceQualityIssue.NONE,
    val featureFrames: List<FloatArray> = emptyList(),
    val preprocessMetrics: VoicePreprocessMetrics? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoiceEmbeddingResult
        return embedding.contentEquals(other.embedding) &&
            qualityIssue == other.qualityIssue &&
            featureFrames.contentDeepEquals(other.featureFrames) &&
            preprocessMetrics == other.preprocessMetrics
    }

    override fun hashCode(): Int {
        var result = embedding.contentHashCode()
        result = 31 * result + qualityIssue.hashCode()
        result = 31 * result + featureFrames.contentDeepHashCode()
        result = 31 * result + (preprocessMetrics?.hashCode() ?: 0)
        return result
    }
}

internal fun List<FloatArray>.contentDeepEquals(other: List<FloatArray>): Boolean {
    return size == other.size && indices.all { index -> this[index].contentEquals(other[index]) }
}

internal fun List<FloatArray>.contentDeepHashCode(): Int {
    var result = 1
    for (array in this) {
        result = 31 * result + array.contentHashCode()
    }
    return result
}
