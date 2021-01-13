package dev.skomlach.biometric.compat.utils;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.os.BuildCompat;

import dev.skomlach.biometric.compat.BiometricApi;
import dev.skomlach.biometric.compat.BiometricAuthRequest;
import dev.skomlach.biometric.compat.BiometricType;
import dev.skomlach.biometric.compat.utils.hardware.Android28Hardware;
import dev.skomlach.biometric.compat.utils.hardware.Android29Hardware;
import dev.skomlach.biometric.compat.utils.hardware.HardwareInfo;
import dev.skomlach.biometric.compat.utils.hardware.LegacyHardware;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class HardwareAccessImpl {
    @Nullable private HardwareInfo hardwareInfo = null;

    private HardwareAccessImpl(BiometricAuthRequest biometricAuthRequest) {
        if (biometricAuthRequest.getApi() == BiometricApi.LEGACY_API) {
            hardwareInfo = new LegacyHardware(biometricAuthRequest);//Android 4+
        } else if (biometricAuthRequest.getApi() == BiometricApi.BIOMETRIC_API) {
            if (BuildCompat.isAtLeastQ()) {
                hardwareInfo = new Android29Hardware(biometricAuthRequest);//new BiometricPrompt API; Has BiometricManager to deal with hasHardware/isEnrolled/isLockedOut
            } else if (BuildCompat.isAtLeastP()) {
                hardwareInfo = new Android28Hardware(biometricAuthRequest); //new BiometricPrompt API; very raw on Android 9, so hacks and workarounds used
            }
        } else {//AUTO
            if (BuildCompat.isAtLeastQ()) {
                hardwareInfo = new Android29Hardware(biometricAuthRequest);//new BiometricPrompt API; Has BiometricManager to deal with hasHardware/isEnrolled/isLockedOut
            } else if (BuildCompat.isAtLeastP()) {
                hardwareInfo = new Android28Hardware(biometricAuthRequest); //new BiometricPrompt API; very raw on Android 9, so hacks and workarounds used
            } else {
                hardwareInfo = new LegacyHardware(biometricAuthRequest);//Android 4+
            }
            if (!(hardwareInfo instanceof LegacyHardware)) {
                LegacyHardware info = new LegacyHardware(biometricAuthRequest);
                if (biometricAuthRequest.getType() == BiometricType.BIOMETRIC_ANY && info.getAvailableBiometricsCount() > 1) {
                    hardwareInfo = info;
                } else if (!isHardwareReady(hardwareInfo) && isHardwareReady(info)) {
                    hardwareInfo = info;
                }
            }
        }
    }

    public static HardwareAccessImpl getInstance(BiometricAuthRequest api) {
        return new HardwareAccessImpl(api);
    }

    private boolean isHardwareReady(HardwareInfo info) {
        return info.isHardwareAvailable() && info.isBiometricEnrolled();
    }

    public boolean isNewBiometricApi() {
        return !(hardwareInfo instanceof LegacyHardware);
    }

    public boolean isHardwareAvailable() {
        return hardwareInfo != null && hardwareInfo.isHardwareAvailable();
    }

    public boolean isBiometricEnrolled() {
        return hardwareInfo != null && hardwareInfo.isBiometricEnrolled();
    }

    public boolean isLockedOut() {
        return hardwareInfo != null && hardwareInfo.isLockedOut();
    }

    public void lockout() {
        if (hardwareInfo != null && !(hardwareInfo instanceof LegacyHardware)) {
            ((Android28Hardware) hardwareInfo).lockout();
        }
    }
}
