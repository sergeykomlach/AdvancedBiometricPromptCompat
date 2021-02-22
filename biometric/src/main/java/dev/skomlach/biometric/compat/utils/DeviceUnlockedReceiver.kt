package dev.skomlach.biometric.compat.utils

import android.content.*
import android.text.TextUtils
import androidx.core.os.BuildCompat
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext.appContext

class DeviceUnlockedReceiver : BroadcastReceiver() {
    companion object {
        fun registerDeviceUnlockListener() {
            if (BuildCompat.isAtLeastN()) {
                try {
                    val filter = IntentFilter()
                    filter.addAction(Intent.ACTION_USER_PRESENT)
                    filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
                    filter.addAction(Intent.ACTION_USER_UNLOCKED)
                    appContext.registerReceiver(DeviceUnlockedReceiver(), filter)
                } catch (e: Throwable) {
                    e(e)
                }
            }
        }
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (!TextUtils.isEmpty(intent.action)) {
            d("Device unlocked or boot completed")
            BiometricErrorLockoutPermanentFix.Companion.INSTANCE.resetBiometricSensorPermanentlyLocked()
        }
    }

}