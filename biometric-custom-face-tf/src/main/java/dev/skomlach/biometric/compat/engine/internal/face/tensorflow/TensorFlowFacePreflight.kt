package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

internal enum class TensorFlowFacePreflightIssue {
    HARDWARE_MISSING,
    CAMERA_BLOCKED,
    CAMERA_IN_USE,
    NO_ENROLLED_BIOMETRIC,
}

internal fun resolveTensorFlowFacePreflightIssue(
    isHardwareDetected: Boolean,
    usesRealCameraProvider: Boolean,
    isCameraBlocked: Boolean,
    isCameraInUse: Boolean,
    isEnrolling: Boolean,
    hasEnrolledBiometric: Boolean
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
    return null
}

internal fun shouldStartTensorFlowFaceSession(isSessionActive: Boolean): Boolean {
    return isSessionActive
}
