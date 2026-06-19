package dev.skomlach.biometric.compat.engine.internal.voice

internal fun matchVoiceTemplates(
    enrolledTemplates: List<VoiceTemplate>,
    probeEmbedding: FloatArray,
    probeFrames: List<FloatArray>,
    topK: Int
): Float {
    return matchVoiceTemplatesDetailed(enrolledTemplates, probeEmbedding, probeFrames, topK).score
}

internal fun matchVoiceTemplatesDetailed(
    enrolledTemplates: List<VoiceTemplate>,
    probeEmbedding: FloatArray,
    probeFrames: List<FloatArray>,
    topK: Int
): VoiceTemplateMatch {
    val gmmModels = if (probeFrames.isNotEmpty()) {
        enrolledTemplates.mapNotNull { it.gmmModel }
    } else {
        emptyList()
    }
    if (gmmModels.isNotEmpty()) {
        val bestDetails = gmmModels
            .map { GmmVoiceTrainer.confidenceDetails(it, probeFrames) }
            .maxByOrNull { it.confidence }
            ?: GmmConfidenceDetails(0f, Float.NEGATIVE_INFINITY, 0f, Float.POSITIVE_INFINITY, 0f, 0, 0)
        return VoiceTemplateMatch(
            score = bestDetails.confidence,
            method = VoiceMatchMethod.GMM,
            templateCount = enrolledTemplates.size,
            gmmModelCount = gmmModels.size,
            probeFrameCount = probeFrames.size,
            gmmDetails = bestDetails
        )
    }

    val scores = enrolledTemplates
        .map { VoiceScorer.score(it.embedding, probeEmbedding) }
        .filter { it > 0f }
        .sortedDescending()
    if (scores.isEmpty()) {
        return VoiceTemplateMatch(
            score = 0f,
            method = VoiceMatchMethod.NONE,
            templateCount = enrolledTemplates.size,
            gmmModelCount = 0,
            probeFrameCount = probeFrames.size,
            gmmDetails = null
        )
    }

    val topScores = scores.take(topK)
    val weighted = topScores.withIndex().sumOf { (index, score) ->
        score.toDouble() * (topK - index)
    }
    val weight = topScores.indices.sumOf { topK - it }.toDouble()
    return VoiceTemplateMatch(
        score = (weighted / weight).toFloat(),
        method = VoiceMatchMethod.EMBEDDING,
        templateCount = enrolledTemplates.size,
        gmmModelCount = 0,
        probeFrameCount = probeFrames.size,
        gmmDetails = null
    )
}

internal data class VoiceTemplateMatch(
    val score: Float,
    val method: VoiceMatchMethod,
    val templateCount: Int,
    val gmmModelCount: Int,
    val probeFrameCount: Int,
    val gmmDetails: GmmConfidenceDetails?
)

internal enum class VoiceMatchMethod {
    GMM,
    EMBEDDING,
    NONE
}
