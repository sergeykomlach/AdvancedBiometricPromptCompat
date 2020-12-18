package dev.skomlach.biometric.compat

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.common.misc.Utils

object BiometricManagerCompat {

    @JvmStatic
    fun isBiometricSensorPermanentlyLocked(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        check(BiometricPromptCompat.isInit) { "Please call BiometricPromptCompat.init(null);  first" }
        return BiometricErrorLockoutPermanentFix.INSTANCE.isBiometricSensorPermanentlyLocked(api.type)
    }
    @JvmStatic
    fun isHardwareDetected(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        check(BiometricPromptCompat.isInit) { "Please call BiometricPromptCompat.init(null);  first" }
        return HardwareAccessImpl.getInstance(api).isHardwareAvailable
    }
    @JvmStatic
    fun hasEnrolled(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        check(BiometricPromptCompat.isInit) { "Please call BiometricPromptCompat.init(null);  first" }
        return HardwareAccessImpl.getInstance(api).isBiometricEnrolled
    }
    @JvmStatic
    fun isLockOut(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        check(BiometricPromptCompat.isInit) { "Please call BiometricPromptCompat.init(null);  first" }
        return HardwareAccessImpl.getInstance(api).isLockedOut
    }
    @JvmStatic
    fun isNewBiometricApi(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        check(BiometricPromptCompat.isInit) { "Please call BiometricPromptCompat.init(null);  first" }
        return HardwareAccessImpl.getInstance(api).isNewBiometricApi
    }
    @JvmStatic
    fun openSettings(
        activity: Activity, api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ) {
        check(BiometricPromptCompat.isInit) { "Please call BiometricPromptCompat.init(null);  first" }
        if (!HardwareAccessImpl.getInstance(api).isNewBiometricApi) {
            BiometricAuthentication.openSettings(activity)
        } else {
            //for unknown reasons on some devices happens SecurityException - "Permission.MANAGE_BIOMETRIC required" - but not should be
            if (Utils.startActivity(Intent("android.settings.BIOMETRIC_ENROLL"), activity)) {
                return
            }
            if (Utils.startActivity(Intent("android.settings.BIOMETRIC_SETTINGS"), activity)) {
                return
            }
            if (Utils.startActivity(
                    Intent().setComponent(
                        ComponentName(
                            "com.android.settings",
                            "com.android.settings.Settings\$BiometricsAndSecuritySettingsActivity"
                        )
                    ), activity
                )
            ) {
                return
            }
            if (Utils.startActivity(
                    Intent().setComponent(
                        ComponentName(
                            "com.android.settings",
                            "com.android.settings.Settings\$SecuritySettingsActivity"
                        )
                    ), activity
                )
            ) {
                return
            }
            Utils.startActivity(
                Intent(Settings.ACTION_SETTINGS), activity
            )
        }
    }

}