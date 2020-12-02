package dev.skomlach.biometric.compat.engine;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public enum BiometricType {
    BIOMETRIC_FINGERPRINT,
    BIOMETRIC_FACE,
    BIOMETRIC_IRIS,
    BIOMETRIC_GENERAL
}