package dev.skomlach.biometric.compat.utils.hardware;

import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.BiometricAuthRequest;
import dev.skomlach.biometric.compat.BiometricType;
import dev.skomlach.biometric.compat.engine.BiometricAuthentication;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.BiometricModule;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class LegacyHardware extends AbstractHardware {
    public LegacyHardware(BiometricAuthRequest authRequest) {
        super(authRequest);
    }

    public int getAvailableBiometricsCount() {
        if (getBiometricAuthRequest().getType() == BiometricType.BIOMETRIC_ANY) {
            int count = 0;
            for (BiometricType type : BiometricAuthentication.getAvailableBiometrics()) {
                BiometricModule biometricModule = BiometricAuthentication.getAvailableBiometricModule(type);
                if (biometricModule != null && biometricModule.isHardwarePresent() && biometricModule.hasEnrolled()) {
                    count++;
                }
            }
            return count;
        }
        BiometricModule biometricModule = BiometricAuthentication.getAvailableBiometricModule(getBiometricAuthRequest().getType());
        return biometricModule != null ? 1 : 0;
    }

    @Override
    public boolean isHardwareAvailable() {
        if (getBiometricAuthRequest().getType() == BiometricType.BIOMETRIC_ANY)
            return BiometricAuthentication.isHardwareDetected();

        BiometricModule biometricModule = BiometricAuthentication.getAvailableBiometricModule(getBiometricAuthRequest().getType());
        return biometricModule != null && biometricModule.isHardwarePresent();
    }

    @Override
    public boolean isBiometricEnrolled() {
        if (getBiometricAuthRequest().getType() == BiometricType.BIOMETRIC_ANY)
            return BiometricAuthentication.hasEnrolled();

        BiometricModule biometricModule = BiometricAuthentication.getAvailableBiometricModule(getBiometricAuthRequest().getType());
        return biometricModule != null && biometricModule.hasEnrolled();
    }

    @Override
    public boolean isLockedOut() {
        if (getBiometricAuthRequest().getType() == BiometricType.BIOMETRIC_ANY)
            return BiometricAuthentication.isLockOut();

        BiometricModule biometricModule = BiometricAuthentication.getAvailableBiometricModule(getBiometricAuthRequest().getType());
        return biometricModule != null && biometricModule.isLockOut();
    }
}
