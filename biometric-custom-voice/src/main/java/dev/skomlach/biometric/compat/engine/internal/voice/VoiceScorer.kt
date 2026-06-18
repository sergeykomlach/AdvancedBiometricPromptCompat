package dev.skomlach.biometric.compat.engine.internal.voice

import kotlin.math.sqrt

object VoiceScorer {
    fun score(enrolled: FloatArray, probe: FloatArray): Float {
        if (!enrolled.isValidEmbedding() || !probe.isValidEmbedding()) return 0f
        val size = minOf(enrolled.size, probe.size)
        var dot = 0.0
        var enrolledNorm = 0.0
        var probeNorm = 0.0
        for (index in 0 until size) {
            val enrolledValue = enrolled[index]
            val probeValue = probe[index]
            dot += enrolledValue * probeValue
            enrolledNorm += enrolledValue * enrolledValue
            probeNorm += probeValue * probeValue
        }
        if (enrolledNorm <= 0.0 || probeNorm <= 0.0) return 0f
        val cosine = dot / (sqrt(enrolledNorm) * sqrt(probeNorm))
        return ((cosine + 1.0) / 2.0).coerceIn(0.0, 1.0).toFloat()
    }
}
