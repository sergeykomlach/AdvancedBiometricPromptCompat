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
    if (score == null || !score.isFinite() || score == Float.MAX_VALUE) {
        return SoftwareBiometricAssurance.UNAVAILABLE
    }
    return if (score >= threshold) {
        SoftwareBiometricAssurance.SPOOF
    } else {
        SoftwareBiometricAssurance.PASS
    }
}
