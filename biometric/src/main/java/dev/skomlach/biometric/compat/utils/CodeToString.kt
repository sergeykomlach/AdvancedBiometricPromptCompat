package dev.skomlach.biometric.compat.utils

import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.engine.BiometricCodes

@RestrictTo(RestrictTo.Scope.LIBRARY)
object CodeToString {
    @JvmStatic
    fun getHelpCode(code: Int): String {
        return when (code) {
            BiometricCodes.BIOMETRIC_ACQUIRED_GOOD -> "BIOMETRIC_ACQUIRED_GOOD"
            BiometricCodes.BIOMETRIC_ACQUIRED_IMAGER_DIRTY -> "BIOMETRIC_ACQUIRED_IMAGER_DIRTY"
            BiometricCodes.BIOMETRIC_ACQUIRED_INSUFFICIENT -> "BIOMETRIC_ACQUIRED_INSUFFICIENT"
            BiometricCodes.BIOMETRIC_ACQUIRED_PARTIAL -> "BIOMETRIC_ACQUIRED_PARTIAL"
            BiometricCodes.BIOMETRIC_ACQUIRED_TOO_FAST -> "BIOMETRIC_ACQUIRED_TOO_FAST"
            BiometricCodes.BIOMETRIC_ACQUIRED_TOO_SLOW -> "BIOMETRIC_ACQUIRED_TOO_SLOW"
            else -> "Unknown BIOMETRIC_ACQUIRED - $code"
        }
    }

    @JvmStatic
    fun getErrorCode(code: Int): String {
        return when (code) {
            BiometricCodes.BIOMETRIC_ERROR_CANCELED -> "BIOMETRIC_ERROR_CANCELED"
            BiometricCodes.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "BIOMETRIC_ERROR_HW_UNAVAILABLE"
            BiometricCodes.BIOMETRIC_ERROR_LOCKOUT -> "BIOMETRIC_ERROR_LOCKOUT"
            BiometricCodes.BIOMETRIC_ERROR_NO_SPACE -> "BIOMETRIC_ERROR_NO_SPACE"
            BiometricCodes.BIOMETRIC_ERROR_TIMEOUT -> "BIOMETRIC_ERROR_TIMEOUT"
            BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS -> "BIOMETRIC_ERROR_UNABLE_TO_PROCESS"
            BiometricCodes.BIOMETRIC_ERROR_HW_NOT_PRESENT -> "BIOMETRIC_ERROR_HW_NOT_PRESENT"
            BiometricCodes.BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> "BIOMETRIC_ERROR_LOCKOUT_PERMANENT"
            BiometricCodes.BIOMETRIC_ERROR_NO_BIOMETRICS -> "BIOMETRIC_ERROR_NO_BIOMETRICS"
            BiometricCodes.BIOMETRIC_ERROR_VENDOR -> "BIOMETRIC_ERROR_VENDOR"
            else -> "Unknown BIOMETRIC_ERROR - $code"
        }
    }
}