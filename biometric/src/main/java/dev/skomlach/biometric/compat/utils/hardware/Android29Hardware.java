package dev.skomlach.biometric.compat.utils.hardware;

import android.annotation.TargetApi;
import android.hardware.biometrics.BiometricManager;
import android.os.Build;

import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.BiometricAuthRequest;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.contextprovider.AndroidContext;
import dev.skomlach.common.misc.Utils;

import static android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE;
import static androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED;
import static androidx.biometric.BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED;
import static androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS;

@TargetApi(Build.VERSION_CODES.Q)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class Android29Hardware extends Android28Hardware {

    public Android29Hardware(BiometricAuthRequest authRequest) {
        super(authRequest);
    }

    private int canAuthenticate() {
        int code = BIOMETRIC_ERROR_NO_HARDWARE;
        try {
            BiometricManager biometricManager = AndroidContext.getAppContext().getSystemService(BiometricManager.class);
            if (biometricManager != null) {
                if (Utils.isAtLeastR()) {
                    code = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
                } else {
                    code = biometricManager.canAuthenticate();
                }
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
        BiometricLoggerImpl.e("Android29Hardware.canAuthenticate - " + code);
        return code;
    }

    @Override
    public boolean isAnyHardwareAvailable() {
        int canAuthenticate = canAuthenticate();
        if (canAuthenticate == BIOMETRIC_SUCCESS) {
            return true;
        } else {
            return canAuthenticate != BIOMETRIC_ERROR_NO_HARDWARE && canAuthenticate != BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED;
        }
    }

    @Override
    public boolean isAnyBiometricEnrolled() {
        int canAuthenticate = canAuthenticate();
        if (canAuthenticate == BIOMETRIC_SUCCESS) {
            return true;
        } else {
            return canAuthenticate != BIOMETRIC_ERROR_NONE_ENROLLED && canAuthenticate != BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED;
        }
    }
}
