package dev.skomlach.biometric.compat.engine

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface BiometricCodes {
    companion object {
        const val BIOMETRIC_ACQUIRED_GOOD = 0
        const val BIOMETRIC_ACQUIRED_IMAGER_DIRTY = 3
        const val BIOMETRIC_ACQUIRED_INSUFFICIENT = 2
        const val BIOMETRIC_ACQUIRED_PARTIAL = 1
        const val BIOMETRIC_ACQUIRED_TOO_FAST = 5
        const val BIOMETRIC_ACQUIRED_TOO_SLOW = 4
        const val BIOMETRIC_ERROR_CANCELED = 5
        const val BIOMETRIC_ERROR_HW_NOT_PRESENT = 12
        const val BIOMETRIC_ERROR_HW_UNAVAILABLE = 1
        const val BIOMETRIC_ERROR_LOCKOUT = 7
        const val BIOMETRIC_ERROR_LOCKOUT_PERMANENT = 9
        const val BIOMETRIC_ERROR_NO_BIOMETRICS = 11
        const val BIOMETRIC_ERROR_NO_SPACE = 4
        const val BIOMETRIC_ERROR_TIMEOUT = 3
        const val BIOMETRIC_ERROR_UNABLE_TO_PROCESS = 2
        const val BIOMETRIC_ERROR_USER_CANCELED = 10
        const val BIOMETRIC_ERROR_VENDOR = 8
        const val BIOMETRIC_ERROR_NEGATIVE_BUTTON = 13
    }
}