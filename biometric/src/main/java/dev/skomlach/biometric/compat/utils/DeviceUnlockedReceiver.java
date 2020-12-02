package dev.skomlach.biometric.compat.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;

import androidx.core.os.BuildCompat;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.contextprovider.AndroidContext;

public class DeviceUnlockedReceiver extends BroadcastReceiver {
    public static void registerDeviceUnlockListener() {
        if (BuildCompat.isAtLeastN()) {
            try {

                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_USER_PRESENT);
                filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED);
                filter.addAction(Intent.ACTION_USER_UNLOCKED);

                AndroidContext.getAppContext().registerReceiver(new DeviceUnlockedReceiver(), filter);
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e);
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TextUtils.isEmpty(intent.getAction())) {
            BiometricLoggerImpl.d("Device unlocked or boot completed");
            BiometricErrorLockoutPermanentFix.INSTANCE.resetBiometricSensorPermanentlyLocked();
        }
    }
}
