package dev.skomlach.biometric.compat.custom

import dev.skomlach.biometric.compat.BiometricType

enum class SoftwareBiometricSecurityDecision {
    ALLOW,
    REJECT_INVALID_PROFILE,
    REJECT_CRYPTO,
    REJECT_MODALITY,
    REJECT_TRUSTED_CAPTURE,
    REJECT_COMPATIBILITY_CAPTURE
}

object SoftwareBiometricSecurityPolicy {
    fun evaluate(
        profile: SoftwareBiometricSecurityProfile,
        requestedType: BiometricType,
        cryptoObject: AbstractSoftwareBiometricManager.CryptoObject?,
        trustedCapture: Boolean,
        compatibilityCapture: Boolean
    ): SoftwareBiometricSecurityDecision {
        if (!profile.isValid()) return SoftwareBiometricSecurityDecision.REJECT_INVALID_PROFILE
        if (profile.biometricType != requestedType && requestedType != BiometricType.BIOMETRIC_ANY) {
            return SoftwareBiometricSecurityDecision.REJECT_MODALITY
        }
        if (cryptoObject != null && !profile.supportsCryptoObject) {
            return SoftwareBiometricSecurityDecision.REJECT_CRYPTO
        }
        if (compatibilityCapture && !profile.allowsCompatibilityCapture) {
            return SoftwareBiometricSecurityDecision.REJECT_COMPATIBILITY_CAPTURE
        }
        if (profile.requiresTrustedCapture && !trustedCapture) {
            return SoftwareBiometricSecurityDecision.REJECT_TRUSTED_CAPTURE
        }
        return SoftwareBiometricSecurityDecision.ALLOW
    }
}
