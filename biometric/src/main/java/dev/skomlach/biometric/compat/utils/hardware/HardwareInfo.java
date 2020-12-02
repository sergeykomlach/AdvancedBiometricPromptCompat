package dev.skomlach.biometric.compat.utils.hardware;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface HardwareInfo {

    boolean isHardwareAvailable();

    boolean isBiometricEnrolled();

    boolean isLockedOut();
}
