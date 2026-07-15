package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceChallengePolicyTest {
    @Test
    fun `same session nonce produces the same challenge sequence`() {
        val first = generateFaceChallenge(sessionNonce = 42L, length = 3)
        val second = generateFaceChallenge(sessionNonce = 42L, length = 3)

        assertEquals(first, second)
    }

    @Test
    fun `challenge sequence starts centered and has requested length`() {
        val challenge = generateFaceChallenge(sessionNonce = 42L, length = 4)

        assertEquals(4, challenge.size)
        assertEquals(FaceChallengeAction.CENTER, challenge.first())
    }

    @Test
    fun `valid sequence completes`() {
        val challenge = listOf(FaceChallengeAction.CENTER, FaceChallengeAction.LEFT)

        assertEquals(
            FaceChallengeDecision.IN_PROGRESS,
            advanceFaceChallenge(challenge, 0, yawDegrees = 0f, toleranceDegrees = 6f)
        )
        assertEquals(
            FaceChallengeDecision.COMPLETE,
            advanceFaceChallenge(challenge, 1, yawDegrees = -15f, toleranceDegrees = 6f)
        )
    }

    @Test
    fun `incomplete sequence does not authenticate`() {
        assertEquals(
            FaceChallengeDecision.IN_PROGRESS,
            advanceFaceChallenge(
                expected = listOf(FaceChallengeAction.CENTER, FaceChallengeAction.RIGHT),
                index = 0,
                yawDegrees = 0f,
                toleranceDegrees = 6f
            )
        )
    }

    @Test
    fun `wrong action rejects the sequence`() {
        assertEquals(
            FaceChallengeDecision.REJECTED,
            advanceFaceChallenge(
                expected = listOf(FaceChallengeAction.LEFT),
                index = 0,
                yawDegrees = 15f,
                toleranceDegrees = 6f
            )
        )
    }

    @Test
    fun `replayed action after completion is rejected`() {
        assertEquals(
            FaceChallengeDecision.REJECTED,
            advanceFaceChallenge(
                expected = listOf(FaceChallengeAction.CENTER),
                index = 1,
                yawDegrees = 0f,
                toleranceDegrees = 6f
            )
        )
    }
}
