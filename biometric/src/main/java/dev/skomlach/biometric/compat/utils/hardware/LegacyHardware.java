package dev.skomlach.biometric.compat.utils.hardware;

import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.engine.BiometricAuthentication;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class LegacyHardware implements HardwareInfo {

    public int getAvailableBiometricsCount() {
        return BiometricAuthentication.getAvailableBiometrics().size();
    }

    @Override
    public boolean isHardwareAvailable() {
        return BiometricAuthentication.isReady() && BiometricAuthentication.isHardwareDetected();
    }

    @Override
    public boolean isBiometricEnrolled() {
        return BiometricAuthentication.hasEnrolled();
    }

    @Override
    public boolean isLockedOut() {
        return BiometricAuthentication.isLockOut();
    }
}
