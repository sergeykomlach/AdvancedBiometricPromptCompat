package dev.skomlach.biometric.compat.custom

import dev.skomlach.biometric.compat.BiometricType

enum class SoftwareBiometricAssuranceLevel {
    LEGACY_COMPATIBILITY,
    PASSIVE_MATCH,
    ACTIVE_CHALLENGE,
    VENDOR_BACKED
}

data class SoftwareBiometricSecurityProfile(
    val biometricType: BiometricType,
    val assurance: SoftwareBiometricAssuranceLevel,
    val requiresTrustedCapture: Boolean,
    val allowsCompatibilityCapture: Boolean,
    val supportsCryptoObject: Boolean,
    val maxCaptureDurationMs: Long
) {
    fun isValid(): Boolean =
        biometricType != BiometricType.BIOMETRIC_ANY &&
            maxCaptureDurationMs in 1..MAX_CAPTURE_DURATION_MS &&
            (!supportsCryptoObject || assurance == SoftwareBiometricAssuranceLevel.VENDOR_BACKED)

    companion object {
        const val MAX_CAPTURE_DURATION_MS = 120_000L

        fun conservativeDefault(type: BiometricType) = SoftwareBiometricSecurityProfile(
            biometricType = type,
            assurance = SoftwareBiometricAssuranceLevel.LEGACY_COMPATIBILITY,
            requiresTrustedCapture = false,
            allowsCompatibilityCapture = true,
            supportsCryptoObject = false,
            maxCaptureDurationMs = 30_000L
        )
    }
}
