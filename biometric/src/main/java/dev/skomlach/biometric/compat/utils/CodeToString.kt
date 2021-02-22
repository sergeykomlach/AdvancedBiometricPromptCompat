package dev.skomlach.biometric.compat.utils;

import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.engine.BiometricCodes;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class CodeToString {

    public static String getHelpCode(int code) {
        switch (code) {
            case BiometricCodes.BIOMETRIC_ACQUIRED_GOOD:
                return ("BIOMETRIC_ACQUIRED_GOOD");

            case BiometricCodes.BIOMETRIC_ACQUIRED_IMAGER_DIRTY:
                return ("BIOMETRIC_ACQUIRED_IMAGER_DIRTY");

            case BiometricCodes.BIOMETRIC_ACQUIRED_INSUFFICIENT:
                return ("BIOMETRIC_ACQUIRED_INSUFFICIENT");

            case BiometricCodes.BIOMETRIC_ACQUIRED_PARTIAL:
                return ("BIOMETRIC_ACQUIRED_PARTIAL");

            case BiometricCodes.BIOMETRIC_ACQUIRED_TOO_FAST:
                return ("BIOMETRIC_ACQUIRED_TOO_FAST");

            case BiometricCodes.BIOMETRIC_ACQUIRED_TOO_SLOW:
                return ("BIOMETRIC_ACQUIRED_TOO_SLOW");

            default:
                return ("Unknown BIOMETRIC_ACQUIRED - " + code);
        }
    }

    public static String getErrorCode(int code) {
        switch (code) {
            case BiometricCodes.BIOMETRIC_ERROR_CANCELED:
                return ("BIOMETRIC_ERROR_CANCELED");

            case BiometricCodes.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return ("BIOMETRIC_ERROR_HW_UNAVAILABLE");

            case BiometricCodes.BIOMETRIC_ERROR_LOCKOUT:
                return ("BIOMETRIC_ERROR_LOCKOUT");

            case BiometricCodes.BIOMETRIC_ERROR_NO_SPACE:
                return ("BIOMETRIC_ERROR_NO_SPACE");

            case BiometricCodes.BIOMETRIC_ERROR_TIMEOUT:
                return ("BIOMETRIC_ERROR_TIMEOUT");

            case BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS:
                return ("BIOMETRIC_ERROR_UNABLE_TO_PROCESS");

            case BiometricCodes.BIOMETRIC_ERROR_HW_NOT_PRESENT:
                return ("BIOMETRIC_ERROR_HW_NOT_PRESENT");

            case BiometricCodes.BIOMETRIC_ERROR_LOCKOUT_PERMANENT:
                return ("BIOMETRIC_ERROR_LOCKOUT_PERMANENT");

            case BiometricCodes.BIOMETRIC_ERROR_NO_BIOMETRICS:
                return ("BIOMETRIC_ERROR_NO_BIOMETRICS");

            case BiometricCodes.BIOMETRIC_ERROR_VENDOR:
                return ("BIOMETRIC_ERROR_VENDOR");

            default:
                return ("Unknown BIOMETRIC_ERROR - " + code);
        }
    }
}
