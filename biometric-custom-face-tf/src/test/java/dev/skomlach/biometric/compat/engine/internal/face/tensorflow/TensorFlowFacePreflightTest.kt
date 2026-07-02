package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TensorFlowFacePreflightTest {

    @Test
    fun resolveTensorFlowFacePreflightIssueReturnsHardwareMissingWhenModelOrCameraUnavailable() {
        assertEquals(
            TensorFlowFacePreflightIssue.HARDWARE_MISSING,
            resolveTensorFlowFacePreflightIssue(
                isHardwareDetected = false,
                usesRealCameraProvider = true,
                isCameraBlocked = false,
                isCameraInUse = false,
                isEnrolling = false,
                hasEnrolledBiometric = true
            )
        )
    }

    @Test
    fun resolveTensorFlowFacePreflightIssueReturnsCameraBlockedForRealCameraSessions() {
        assertEquals(
            TensorFlowFacePreflightIssue.CAMERA_BLOCKED,
            resolveTensorFlowFacePreflightIssue(
                isHardwareDetected = true,
                usesRealCameraProvider = true,
                isCameraBlocked = true,
                isCameraInUse = false,
                isEnrolling = false,
                hasEnrolledBiometric = true
            )
        )
    }

    @Test
    fun resolveTensorFlowFacePreflightIssueReturnsCameraInUseForRealCameraSessions() {
        assertEquals(
            TensorFlowFacePreflightIssue.CAMERA_IN_USE,
            resolveTensorFlowFacePreflightIssue(
                isHardwareDetected = true,
                usesRealCameraProvider = true,
                isCameraBlocked = false,
                isCameraInUse = true,
                isEnrolling = false,
                hasEnrolledBiometric = true
            )
        )
    }

    @Test
    fun resolveTensorFlowFacePreflightIssueReturnsNoEnrolledBiometricForAuthWithoutTemplates() {
        assertEquals(
            TensorFlowFacePreflightIssue.NO_ENROLLED_BIOMETRIC,
            resolveTensorFlowFacePreflightIssue(
                isHardwareDetected = true,
                usesRealCameraProvider = false,
                isCameraBlocked = false,
                isCameraInUse = false,
                isEnrolling = false,
                hasEnrolledBiometric = false
            )
        )
    }

    @Test
    fun resolveTensorFlowFacePreflightIssueAllowsEnrollmentWithoutTemplates() {
        assertNull(
            resolveTensorFlowFacePreflightIssue(
                isHardwareDetected = true,
                usesRealCameraProvider = false,
                isCameraBlocked = false,
                isCameraInUse = false,
                isEnrolling = true,
                hasEnrolledBiometric = false
            )
        )
    }
}
