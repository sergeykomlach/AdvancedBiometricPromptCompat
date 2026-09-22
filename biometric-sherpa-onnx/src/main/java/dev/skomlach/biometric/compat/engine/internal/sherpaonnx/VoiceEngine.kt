package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

/**
 * Separate interface preserving VoiceEngine's JVM ABI; required for enrollment/authentication.
 * Custom engines must expose
 * a stable algorithm + model/preprocessing identity; change it when the embedding space changes.
 */
interface VoiceTemplateIdentityProvider {
    /** Cached identity only. Expensive preparation belongs in [PreparingVoiceEngine.prepare]. */
    val templateIdentity: String?
}

/** Optional extension: invoked on the manager worker before capture/authentication. */
interface PreparingVoiceEngine {
    fun prepare(): Boolean
}

internal fun prepareVoiceEngine(engine: VoiceEngine): Boolean = try {
    val available = (engine as? PreparingVoiceEngine)?.prepare() ?: engine.isAvailable()
    available && !(engine as? VoiceTemplateIdentityProvider)?.templateIdentity.isNullOrBlank()
} catch (_: Exception) {
    false
} catch (_: LinkageError) {
    false
}

internal fun voiceTemplateIdentityMatches(stored: String?, expected: String?): Boolean =
    !expected.isNullOrBlank() && stored == expected

/** Earlier training could retain outliers; do not merge or authenticate those profiles. */
internal fun voiceEnrollmentIdentity(engine: VoiceEngine): String? =
    (engine as? VoiceTemplateIdentityProvider)?.templateIdentity
        ?.takeIf { it.isNotBlank() }
        ?.let { "voice-consensus-v2:$it" }

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

