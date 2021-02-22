package dev.skomlach.biometric.compat.utils;

import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.RestrictTo;

import dev.skomlach.common.cryptostorage.SharedPreferenceProvider;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BiometricAuthWasCanceledByError {
    private static final String TS_PREF = "error_cancel";
    public static BiometricAuthWasCanceledByError INSTANCE = new BiometricAuthWasCanceledByError();
    private final SharedPreferences preferences;

    private BiometricAuthWasCanceledByError() {
        preferences = SharedPreferenceProvider.getCryptoPreferences("BiometricModules");
    }

    public void setCanceledByError() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P)
            preferences.edit().putBoolean(TS_PREF, true).apply();
    }

    public void resetCanceledByError() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P)
            preferences.edit().putBoolean(TS_PREF, false).apply();
    }

    public boolean isCanceledByError() {
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.P && preferences.getBoolean(TS_PREF, false);
    }
}
