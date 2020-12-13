package dev.skomlach.biometric.compat.utils.hardware;

import dev.skomlach.biometric.compat.BiometricAuthRequest;

abstract class AbstractHardware implements HardwareInfo {
    private final BiometricAuthRequest authRequest;

    AbstractHardware(BiometricAuthRequest authRequest) {
        this.authRequest = authRequest;
    }

    public final BiometricAuthRequest getBiometricAuthRequest() {
        return authRequest;
    }

    @Override
    public abstract boolean isHardwareAvailable();

    @Override
    public abstract boolean isBiometricEnrolled();

    @Override
    public abstract boolean isLockedOut();
}
