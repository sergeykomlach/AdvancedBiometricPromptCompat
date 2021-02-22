package dev.skomlach.biometric.compat.utils;

import android.content.SharedPreferences;

import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.BiometricType;
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BiometricErrorLockoutPermanentFix {
    private static final String TS_PREF = "user_unlock_device";
    public static BiometricErrorLockoutPermanentFix INSTANCE = new BiometricErrorLockoutPermanentFix();
    private final SharedPreferences sharedPreferences;

    private BiometricErrorLockoutPermanentFix() {
        sharedPreferences = SharedPreferenceProvider.getCryptoPreferences("BiometricErrorLockoutPermanentFix");
    }

    public void setBiometricSensorPermanentlyLocked(BiometricType type) {
        sharedPreferences.edit().putBoolean(TS_PREF + "-" + type.name(), false).apply();
    }

    void resetBiometricSensorPermanentlyLocked() {
        sharedPreferences.edit().clear().apply();
    }

    public boolean isBiometricSensorPermanentlyLocked(BiometricType type) {
        return !sharedPreferences.getBoolean(TS_PREF + "-" + type.name(), true);
    }
}
