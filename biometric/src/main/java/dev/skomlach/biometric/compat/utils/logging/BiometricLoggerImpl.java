package dev.skomlach.biometric.compat.utils.logging;

import android.util.Log;

import androidx.annotation.RestrictTo;

import java.util.Arrays;

import dev.skomlach.biometric.compat.BuildConfig;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BiometricLoggerImpl {

    public static void e(Object... msgs) {
        if (BuildConfig.DEBUG)
            Log.e("BiometricLogging", Arrays.asList(msgs).toString());
    }

    public static void e(Throwable e) {
        e(e, e.getMessage());
    }

    public static void e(Throwable e, Object... msgs) {
        if (BuildConfig.DEBUG)
            Log.e("BiometricLogging", Arrays.asList(msgs).toString(), e);
    }

    public static void d(Object... msgs) {
        if (BuildConfig.DEBUG)
            Log.d("BiometricLogging", Arrays.asList(msgs).toString());
    }
}
