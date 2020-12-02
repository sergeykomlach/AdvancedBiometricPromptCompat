package dev.skomlach.biometric.compat.engine.internal;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.engine.BiometricCodes;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.BiometricModule;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.contextprovider.AndroidContext;
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider;

import java.util.concurrent.TimeUnit;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public abstract class AbstractBiometricModule implements BiometricModule, BiometricCodes {

    //LockOut behavior emulated, because for example Meizu API allow to enroll fingerprint unlimited times
    private static final String TS_PREF = "timestamp_";
    private static final long timeout = TimeUnit.SECONDS.toMillis(31);
    private final int tag;
    private final SharedPreferences preferences;

    public AbstractBiometricModule(int tag) {
        this.tag = tag;
        preferences = SharedPreferenceProvider.getCryptoPreferences("BiometricModules");
    }

    public Context getContext() {
        return AndroidContext.getAppContext();
    }

    public void lockout() {
        if (!isLockOut()) {
            BiometricLoggerImpl.d("AbstractBiometricModule: setLockout for " + tag());
            preferences.edit().putLong(TS_PREF + tag(), System.currentTimeMillis()).apply();
        }
    }

    @Override
    public int tag() {
        return tag;
    }

    @Override
    public boolean isLockOut() {
        long ts = preferences.getLong(TS_PREF + tag(), 0);

        if (ts > 0) {
            if (System.currentTimeMillis() - ts >= timeout) {
                preferences.edit().putLong(TS_PREF + tag(), 0).apply();
                BiometricLoggerImpl.d("AbstractBiometricModule: lockout is FALSE(1) for " + tag());
                return false;
            } else {
                BiometricLoggerImpl.d("AbstractBiometricModule: lockout is TRUE for " + tag());
                return true;
            }
        } else {
            BiometricLoggerImpl.d("AbstractBiometricModule: lockout is FALSE(2) for " + tag());
            return false;
        }
    }
}
