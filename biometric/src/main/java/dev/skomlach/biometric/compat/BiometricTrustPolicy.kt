package dev.skomlach.biometric.compat

import dev.skomlach.biometric.compat.custom.SoftwareBiometricAssuranceLevel
import dev.skomlach.biometric.compat.custom.SoftwareBiometricSecurityProfile

/** Capability requirements, not Android certification levels or a ranking of match scores. */
enum class BiometricTrustLevel {
    COMPATIBILITY,
    TRUSTED_CAPTURE,
    SYSTEM_BIOMETRIC
}

/** Frozen for each authentication operation; enrollment and cryptographic binding stay separate. */
data class BiometricTrustPolicy(
    val minimumTrust: BiometricTrustLevel = BiometricTrustLevel.TRUSTED_CAPTURE,
    val allowBehaviorOnlySuccess: Boolean = false
) {
    internal fun permitsAuthentication(
        type: BiometricType,
        systemBiometric: Boolean,
        profile: SoftwareBiometricSecurityProfile?,
        trustedCapture: Boolean
    ): Boolean {
        if (type == BiometricType.BIOMETRIC_ANY) return false
        if (type == BiometricType.BIOMETRIC_BEHAVIOR && !allowBehaviorOnlySuccess) return false
        if (systemBiometric) return type != BiometricType.BIOMETRIC_BEHAVIOR
        if (profile == null || !profile.isValid() || profile.biometricType != type) return false
        return when (minimumTrust) {
            BiometricTrustLevel.COMPATIBILITY -> true
            BiometricTrustLevel.SYSTEM_BIOMETRIC -> false
            BiometricTrustLevel.TRUSTED_CAPTURE -> profile.requiresTrustedCapture &&
                !profile.allowsCompatibilityCapture && trustedCapture &&
                profile.assurance in setOf(SoftwareBiometricAssuranceLevel.PASSIVE_MATCH,
                    SoftwareBiometricAssuranceLevel.ACTIVE_CHALLENGE, SoftwareBiometricAssuranceLevel.VENDOR_BACKED)
        }
    }

    internal fun permitsExecution(
        type: BiometricType,
        systemBiometric: Boolean,
        profile: SoftwareBiometricSecurityProfile?,
        trustedCapture: Boolean,
        supplemental: Boolean
    ): Boolean = if (supplemental) {
        type == BiometricType.BIOMETRIC_BEHAVIOR && !systemBiometric &&
            profile?.isValid() == true && profile.biometricType == type
    } else permitsAuthentication(type, systemBiometric, profile, trustedCapture)
}

enum class SupplementalBiometricOutcome { MATCHED, NOT_MATCHED, UNAVAILABLE }

/** Advisory evidence only. Never included in AuthenticationCallback.onSucceeded's factor set. */
data class SupplementalBiometricResult(
    val type: BiometricType,
    val outcome: SupplementalBiometricOutcome,
    val failureReasons: Set<AuthenticationFailureReason> = emptySet()
)

/** ALL retains its operation's admitted factors even if a route later becomes unavailable. */
internal fun resolveAuthenticationTrustScope(
    confirmation: BiometricConfirmation,
    admitted: Set<BiometricType>?,
    live: Set<BiometricType>,
    stage: Set<BiometricType>?
): Set<BiometricType> {
    val required = if (confirmation == BiometricConfirmation.ALL) admitted ?: live else live
    return stage?.let { required.intersect(it) } ?: required
}
