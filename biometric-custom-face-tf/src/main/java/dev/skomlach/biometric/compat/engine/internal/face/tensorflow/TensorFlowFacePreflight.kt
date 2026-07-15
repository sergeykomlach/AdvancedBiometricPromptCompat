package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

internal enum class TensorFlowFacePreflightIssue {
    HARDWARE_MISSING,
    CAMERA_BLOCKED,
    CAMERA_IN_USE,
    NO_ENROLLED_BIOMETRIC,
    ANTI_SPOOFING_UNAVAILABLE,
    UNTRUSTED_CAPTURE_PROVIDER,
}

internal fun resolveTensorFlowFacePreflightIssue(
    isHardwareDetected: Boolean,
    usesRealCameraProvider: Boolean,
    isCameraBlocked: Boolean,
    isCameraInUse: Boolean,
    isEnrolling: Boolean,
    hasEnrolledBiometric: Boolean,
    antiSpoofingAvailable: Boolean = true,
    requireAntiSpoofing: Boolean = false,
    requireRealCameraProvider: Boolean = false
): TensorFlowFacePreflightIssue? {
    if (!isHardwareDetected) {
        return TensorFlowFacePreflightIssue.HARDWARE_MISSING
    }
    if (usesRealCameraProvider && isCameraBlocked) {
        return TensorFlowFacePreflightIssue.CAMERA_BLOCKED
    }
    if (usesRealCameraProvider && isCameraInUse) {
        return TensorFlowFacePreflightIssue.CAMERA_IN_USE
    }
    if (!isEnrolling && !hasEnrolledBiometric) {
        return TensorFlowFacePreflightIssue.NO_ENROLLED_BIOMETRIC
    }
    if (!isEnrolling && requireRealCameraProvider && !usesRealCameraProvider) {
        return TensorFlowFacePreflightIssue.UNTRUSTED_CAPTURE_PROVIDER
    }
    if (!isEnrolling && requireAntiSpoofing && !antiSpoofingAvailable) {
        return TensorFlowFacePreflightIssue.ANTI_SPOOFING_UNAVAILABLE
    }
    return null
}

internal fun shouldStartTensorFlowFaceSession(isSessionActive: Boolean): Boolean {
    return isSessionActive
}
