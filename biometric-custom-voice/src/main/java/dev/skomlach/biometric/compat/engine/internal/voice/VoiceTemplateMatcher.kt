package dev.skomlach.biometric.compat.engine.internal.voice

internal fun matchVoiceTemplates(
    enrolledTemplates: List<VoiceTemplate>,
    probeEmbedding: FloatArray,
    probeFrames: List<FloatArray>,
    topK: Int
): Float {
    val gmmModels = if (probeFrames.isNotEmpty()) {
        enrolledTemplates.mapNotNull { it.gmmModel }
    } else {
        emptyList()
    }
    if (gmmModels.isNotEmpty()) {
        return gmmModels.maxOf { GmmVoiceTrainer.confidence(it, probeFrames) }
    }

    val scores = enrolledTemplates
        .map { VoiceScorer.score(it.embedding, probeEmbedding) }
        .filter { it > 0f }
        .sortedDescending()
    if (scores.isEmpty()) return 0f

    val topScores = scores.take(topK)
    val weighted = topScores.withIndex().sumOf { (index, score) ->
        score.toDouble() * (topK - index)
    }
    val weight = topScores.indices.sumOf { topK - it }.toDouble()
    return (weighted / weight).toFloat()
}
