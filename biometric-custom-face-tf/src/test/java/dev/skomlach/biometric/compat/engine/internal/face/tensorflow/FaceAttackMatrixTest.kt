package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceAttackMatrixTest {
    @Test
    fun knownFaceAttackVectorsAreRejectedByPolicy() {
        val vectors = listOf(
            "invalid-pad-output" to
                (classifyFaceAntiSpoofingScore(Float.NaN, 0.28f) ==
                    dev.skomlach.biometric.compat.custom.SoftwareBiometricAssurance.UNAVAILABLE),
            "spoof-score" to
                (classifyFaceAntiSpoofingScore(0.8f, 0.28f) ==
                    dev.skomlach.biometric.compat.custom.SoftwareBiometricAssurance.SPOOF),
            "wrong-challenge-action" to
                (advanceFaceChallenge(
                    listOf(FaceChallengeAction.LEFT), 0, 15f, 6f
                ) == FaceChallengeDecision.REJECTED),
            "expired-challenge" to
                (evaluateFaceChallengeGuard(1_000L, 6_001L, 0, 3, 5_000L) ==
                    FaceChallengeGuardDecision.TIMEOUT)
        )

        assertEquals(emptyList<String>(), vectors.filterNot { it.second }.map { it.first })
    }
}
