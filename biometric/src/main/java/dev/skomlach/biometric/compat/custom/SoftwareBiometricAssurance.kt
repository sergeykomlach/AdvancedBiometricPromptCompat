package dev.skomlach.biometric.compat.custom

/**
 * Security outcome produced by a software biometric pipeline.
 *
 * Only [PASS] is an authentication success. All other states must remain
 * distinguishable so callers can choose a safe fallback or a step-up flow.
 */
enum class SoftwareBiometricAssurance {
    PASS,
    SPOOF,
    MISMATCH,
    UNAVAILABLE,
    CAPTURE_UNTRUSTED;

    fun isAcceptedAsAuthenticated(): Boolean = this == PASS
}
