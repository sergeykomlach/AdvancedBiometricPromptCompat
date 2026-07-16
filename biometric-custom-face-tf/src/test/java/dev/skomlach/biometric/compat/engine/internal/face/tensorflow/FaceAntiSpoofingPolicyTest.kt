package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import dev.skomlach.biometric.compat.custom.SoftwareBiometricAssurance
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceAntiSpoofingPolicyTest {
    @Test
    fun compatibilityDefaultsDoNotRequireChallengeOrPadAvailability() {
        val config = TensorFlowFaceConfig()

        assertEquals(false, config.faceChallengeEnabled)
        assertEquals(false, config.requireAntiSpoofingForAuthentication)
    }

    @Test
    fun classifiesScoreAgainstConfiguredThreshold() {
        assertEquals(
            SoftwareBiometricAssurance.PASS,
            classifyFaceAntiSpoofingScore(score = 0.1f, threshold = 0.28f)
        )
        assertEquals(
            SoftwareBiometricAssurance.SPOOF,
            classifyFaceAntiSpoofingScore(score = 0.4f, threshold = 0.28f)
        )
    }

    @Test
    fun invalidScoreIsUnavailableAndCannotPass() {
        assertEquals(
            SoftwareBiometricAssurance.UNAVAILABLE,
            classifyFaceAntiSpoofingScore(score = null, threshold = 0.28f)
        )
        assertEquals(
            SoftwareBiometricAssurance.UNAVAILABLE,
            classifyFaceAntiSpoofingScore(score = Float.NaN, threshold = 0.28f)
        )
        assertEquals(
            SoftwareBiometricAssurance.UNAVAILABLE,
            classifyFaceAntiSpoofingScore(score = Float.MAX_VALUE, threshold = 0.28f)
        )
    }

    @Test
    fun strictAuthenticationDoesNotStartWithoutAntiSpoofing() {
        assertEquals(
            TensorFlowFacePreflightIssue.ANTI_SPOOFING_UNAVAILABLE,
            resolveTensorFlowFacePreflightIssue(
                isHardwareDetected = true,
                usesRealCameraProvider = true,
                isCameraBlocked = false,
                isCameraInUse = false,
                isEnrolling = false,
                hasEnrolledBiometric = true,
                antiSpoofingAvailable = false,
                requireAntiSpoofing = true
            )
        )
    }

    @Test
    fun unavailablePadIsAllowedOnlyWhenAuthenticationDoesNotRequireIt() {
        assertEquals(
            true,
            isFaceAntiSpoofingAccepted(
                decision = SoftwareBiometricAssurance.UNAVAILABLE,
                requiredForAuthentication = false
            )
        )
        assertEquals(
            false,
            isFaceAntiSpoofingAccepted(
                decision = SoftwareBiometricAssurance.UNAVAILABLE,
                requiredForAuthentication = true
            )
        )
    }

    @Test
    fun spoofIsRejectedRegardlessOfPadRequirement() {
        assertEquals(
            false,
            isFaceAntiSpoofingAccepted(
                decision = SoftwareBiometricAssurance.SPOOF,
                requiredForAuthentication = false
            )
        )
    }

    @Test
    fun optionalAntiSpoofingDoesNotBlockEnrolledAuthenticationWhenModelIsUnavailable() {
        assertEquals(
            null,
            resolveTensorFlowFacePreflightIssue(
                isHardwareDetected = true,
                usesRealCameraProvider = true,
                isCameraBlocked = false,
                isCameraInUse = false,
                isEnrolling = false,
                hasEnrolledBiometric = true,
                antiSpoofingAvailable = false,
                requireAntiSpoofing = false
            )
        )
    }
}
