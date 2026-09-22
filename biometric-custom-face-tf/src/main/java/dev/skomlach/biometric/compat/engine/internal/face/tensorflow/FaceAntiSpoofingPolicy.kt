package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import dev.skomlach.biometric.compat.custom.SoftwareBiometricAssurance

internal fun isFaceAntiSpoofingAccepted(
    decision: SoftwareBiometricAssurance,
    requiredForAuthentication: Boolean
): Boolean {
    return when (decision) {
        SoftwareBiometricAssurance.PASS -> true
        SoftwareBiometricAssurance.SPOOF,
        SoftwareBiometricAssurance.MISMATCH,
        SoftwareBiometricAssurance.CAPTURE_UNTRUSTED -> false
        SoftwareBiometricAssurance.UNAVAILABLE -> !requiredForAuthentication
    }
}

internal fun classifyFaceAntiSpoofingScore(
    score: Float?,
    threshold: Float
): SoftwareBiometricAssurance {
    if (score == null || !score.isFinite() || score < 0f || score == Float.MAX_VALUE) {
        return SoftwareBiometricAssurance.UNAVAILABLE
    }
    return if (score >= threshold) {
        SoftwareBiometricAssurance.SPOOF
    } else {
        SoftwareBiometricAssurance.PASS
    }
}

internal enum class FaceAntiSpoofingDecision { PENDING, PASS, SPOOF, UNAVAILABLE }

internal fun isFaceLivenessAccepted(
    decision: FaceAntiSpoofingDecision,
    required: Boolean
): Boolean = decision == FaceAntiSpoofingDecision.PASS ||
    (decision == FaceAntiSpoofingDecision.UNAVAILABLE && !required)

/** Evidence belongs to one candidate in one capture session, never to the manager lifetime. */
internal class FaceAntiSpoofingWindow(
    private val windowSize: Int,
    private val minimumFrames: Int,
    private val threshold: Float
) {
    init {
        require(minimumFrames > 0 && windowSize >= minimumFrames)
        require(threshold.isFinite() && threshold in 0f..1f)
    }

    private val scores = ArrayDeque<Float>()

    @Synchronized
    fun reset() = scores.clear()

    @Synchronized
    fun add(score: Float?): FaceAntiSpoofingDecision {
        if (classifyFaceAntiSpoofingScore(score, threshold) == SoftwareBiometricAssurance.UNAVAILABLE) {
            reset()
            return FaceAntiSpoofingDecision.UNAVAILABLE
        }
        val validScore = requireNotNull(score)
        scores.addLast(validScore)
        while (scores.size > windowSize) scores.removeFirst()
        if (scores.size < minimumFrames) return FaceAntiSpoofingDecision.PENDING
        val spoof = scores.average() >= threshold && validScore >= threshold &&
            scores.count { it >= threshold } >= minimumFrames
        return if (spoof) FaceAntiSpoofingDecision.SPOOF else FaceAntiSpoofingDecision.PASS
    }
}
