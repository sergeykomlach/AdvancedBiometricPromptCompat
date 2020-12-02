package dev.skomlach.biometric.compat.utils;

import androidx.annotation.RestrictTo;
import androidx.core.os.BuildCompat;

import dev.skomlach.biometric.compat.BiometricApi;
import dev.skomlach.biometric.compat.utils.hardware.Android28Hardware;
import dev.skomlach.biometric.compat.utils.hardware.Android29Hardware;
import dev.skomlach.biometric.compat.utils.hardware.HardwareInfo;
import dev.skomlach.biometric.compat.utils.hardware.LegacyHardware;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class HardwareAccessImpl {
    private HardwareInfo hardwareInfo;

    private HardwareAccessImpl(BiometricApi api) {
        if (api == BiometricApi.LEGACY_API || DevicesWithKnownBugs.isLGWithBiometricBug()) {
            hardwareInfo = new LegacyHardware();//Android 4+
        } else {
            if (BuildCompat.isAtLeastQ()) {
                hardwareInfo = new Android29Hardware();//new BiometricPrompt API; Has BiometricManager to deal with hasHardware/isEnrolled/isLockedOut
            } else if (BuildCompat.isAtLeastP()) {
                hardwareInfo = new Android28Hardware(); //new BiometricPrompt API; very raw on Android 9, so hacks and workarounds used
            } else {
                hardwareInfo = new LegacyHardware();//Android 4+
            }
            if (api == BiometricApi.AUTO && !(hardwareInfo instanceof LegacyHardware)) {
                LegacyHardware info = new LegacyHardware();
                if (info.getAvailableBiometricsCount() > 1 || (!isHardwareReady(hardwareInfo) && isHardwareReady(info))) {
                    hardwareInfo = info;
                }
            }
        }
    }

    public static HardwareAccessImpl getInstance(BiometricApi api) {
        return new HardwareAccessImpl(api);
    }

    private boolean isHardwareReady(HardwareInfo info) {
        return info.isHardwareAvailable() && info.isBiometricEnrolled();
    }

    public boolean isNewBiometricApi() {
        return !(hardwareInfo instanceof LegacyHardware);
    }

    public boolean isHardwareAvailable() {
        return hardwareInfo.isHardwareAvailable();
    }

    public boolean isBiometricEnrolled() {
        return hardwareInfo.isBiometricEnrolled();
    }

    public boolean isLockedOut() {
        return hardwareInfo.isLockedOut();
    }

    public void lockout() {
        if (!(hardwareInfo instanceof LegacyHardware)) {
            ((Android28Hardware) hardwareInfo).lockout();
        }
    }
}
