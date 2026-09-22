package dev.skomlach.biometric.compat.impl

import androidx.biometric.BiometricPrompt
import dev.skomlach.biometric.compat.AuthenticationFailureReason

internal fun shouldApplyExhaustedLegacyLockout(
    fromSystemPrompt: Boolean, reason: AuthenticationFailureReason?
): Boolean = !fromSystemPrompt && (reason == AuthenticationFailureReason.SENSOR_FAILED ||
    reason == AuthenticationFailureReason.AUTHENTICATION_FAILED)

/** All non-cancellation errors close the native branch through the normal completion policy. */
internal fun nativePromptFailureReason(code: Int): AuthenticationFailureReason =
    when (if (code < 1000) code else code % 1000) {
        BiometricPrompt.ERROR_NO_BIOMETRICS -> AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
        BiometricPrompt.ERROR_HW_NOT_PRESENT -> AuthenticationFailureReason.NO_HARDWARE
        BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> AuthenticationFailureReason.HARDWARE_UNAVAILABLE
        BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> AuthenticationFailureReason.AUTHENTICATION_FAILED
        BiometricPrompt.ERROR_NO_SPACE -> AuthenticationFailureReason.SENSOR_FAILED
        BiometricPrompt.ERROR_TIMEOUT -> AuthenticationFailureReason.TIMEOUT
        BiometricPrompt.ERROR_LOCKOUT -> AuthenticationFailureReason.LOCKED_OUT
        BiometricPrompt.ERROR_CANCELED -> AuthenticationFailureReason.CANCELED
        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON ->
            AuthenticationFailureReason.CANCELED_BY_USER
        else -> AuthenticationFailureReason.UNKNOWN
    }
