package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import kotlin.math.abs
import kotlin.random.Random

internal enum class FaceChallengeAction {
    CENTER,
    LEFT,
    RIGHT
}

/** A completed challenge is reusable only for the same continuously matched identity. */
internal class FaceChallengeIdentityBinding {
    private var candidateId: String? = null

    fun reset() { candidateId = null }

    /** Returns true when existing challenge evidence must be discarded. */
    fun update(matchedCandidateId: String?): Boolean {
        val changed = candidateId != null && candidateId != matchedCandidateId
        candidateId = matchedCandidateId
        return changed
    }
}

internal enum class FaceChallengeDecision {
    IN_PROGRESS,
    COMPLETE,
    REJECTED
}

internal enum class FaceChallengeGuardDecision {
    ALLOW,
    TIMEOUT,
    ATTEMPTS_EXCEEDED
}

internal fun evaluateFaceChallengeGuard(
    stepStartedAtMs: Long,
    nowMs: Long,
    rejectedAttempts: Int,
    maxRejectedAttempts: Int,
    stepTimeoutMs: Long
): FaceChallengeGuardDecision {
    if (rejectedAttempts >= maxRejectedAttempts) {
        return FaceChallengeGuardDecision.ATTEMPTS_EXCEEDED
    }
    if (nowMs - stepStartedAtMs >= stepTimeoutMs) {
        return FaceChallengeGuardDecision.TIMEOUT
    }
    return FaceChallengeGuardDecision.ALLOW
}

internal fun shouldCountFaceSpoofFailure(isEnrolling: Boolean): Boolean = !isEnrolling

internal fun generateFaceChallenge(
    sessionNonce: Long,
    length: Int
): List<FaceChallengeAction> {
    require(length >= 2) { "Face challenge must contain at least two actions" }
    val random = Random(sessionNonce)
    return buildList(length) {
        add(FaceChallengeAction.CENTER)
        repeat(length - 1) {
            add(if (random.nextBoolean()) FaceChallengeAction.LEFT else FaceChallengeAction.RIGHT)
        }
    }
}

internal fun advanceFaceChallenge(
    expected: List<FaceChallengeAction>,
    index: Int,
    yawDegrees: Float,
    toleranceDegrees: Float,
    targetYawMagnitudeDegrees: Float = 15f
): FaceChallengeDecision {
    if (index !in expected.indices || !yawDegrees.isFinite() ||
        !toleranceDegrees.isFinite() || toleranceDegrees < 0f ||
        !targetYawMagnitudeDegrees.isFinite() || targetYawMagnitudeDegrees <= 0f
    ) {
        return FaceChallengeDecision.REJECTED
    }

    val targetYaw = when (expected[index]) {
        FaceChallengeAction.CENTER -> 0f
        FaceChallengeAction.LEFT -> -targetYawMagnitudeDegrees
        FaceChallengeAction.RIGHT -> targetYawMagnitudeDegrees
    }
    if (abs(yawDegrees - targetYaw) > toleranceDegrees) {
        return FaceChallengeDecision.REJECTED
    }
    return if (index == expected.lastIndex) {
        FaceChallengeDecision.COMPLETE
    } else {
        FaceChallengeDecision.IN_PROGRESS
    }
}
