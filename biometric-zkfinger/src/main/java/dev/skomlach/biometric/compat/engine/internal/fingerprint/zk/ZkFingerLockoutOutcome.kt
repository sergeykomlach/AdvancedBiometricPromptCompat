package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.zkfinger.R

internal data class ZkFingerLockoutOutcome(
    val reason: AuthenticationFailureReason,
    val messageResId: Int
)

internal fun zkFingerLockoutOutcomeForError(error: Int): ZkFingerLockoutOutcome {
    return ZkFingerLockoutOutcome(
        reason = if (error == AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT) {
            AuthenticationFailureReason.HARDWARE_UNAVAILABLE
        } else {
            AuthenticationFailureReason.LOCKED_OUT
        },
        messageResId = if (error == AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT) {
            R.string.biometriccompat_zkfinger_help_too_many_attempts_permanent
        } else {
            R.string.biometriccompat_zkfinger_help_too_many_attempts_try_later
        }
    )
}
