package dev.skomlach.biometric.compat

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider
import dev.skomlach.common.misc.Utils
import me.weishu.reflection.Reflection
import org.ifaa.android.manager.IFAAManagerFactory
import java.util.*

object BiometricManagerCompat {

    private fun getLastKnown(name: String): Boolean {
        return SharedPreferenceProvider.getCryptoPreferences(name).getBoolean(name, false)
    }

    private fun setLastKnown(name: String, value: Boolean) {
        return SharedPreferenceProvider.getCryptoPreferences(name).edit().putBoolean(name, value)
            .apply()
    }

    @JvmStatic
    fun isBiometricSensorPermanentlyLocked(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        return if (BiometricPromptCompat.isInit) {
            val result =
                BiometricErrorLockoutPermanentFix.INSTANCE.isBiometricSensorPermanentlyLocked(api.type)
            setLastKnown("isBiometricSensorPermanentlyLocked-${api.api}-${api.type}", result)
            result
        } else
            getLastKnown("isBiometricSensorPermanentlyLocked-${api.api}-${api.type}")
    }
    @JvmStatic
    fun isHardwareDetected(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        return if (BiometricPromptCompat.isInit) {
            val result =
                HardwareAccessImpl.getInstance(api).isHardwareAvailable
            setLastKnown("isHardwareAvailable-${api.api}-${api.type}", result)
            result
        } else
            getLastKnown("isHardwareAvailable-${api.api}-${api.type}")
    }
    @JvmStatic
    fun hasEnrolled(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        return if (BiometricPromptCompat.isInit) {
            val result =
                HardwareAccessImpl.getInstance(api).isBiometricEnrolled
            setLastKnown("isBiometricEnrolled-${api.api}-${api.type}", result)
            result
        } else
            getLastKnown("isBiometricEnrolled-${api.api}-${api.type}")
    }
    @JvmStatic
    fun isLockOut(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        return if (BiometricPromptCompat.isInit) {
            val result =
                HardwareAccessImpl.getInstance(api).isLockedOut
            setLastKnown("isLockedOut-${api.api}-${api.type}", result)
            result
        } else
            getLastKnown("isLockedOut-${api.api}-${api.type}")
    }
    @JvmStatic
    fun isNewBiometricApi(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        return if (BiometricPromptCompat.isInit) {
            val result =
                HardwareAccessImpl.getInstance(api).isNewBiometricApi
            setLastKnown("isNewBiometricApi-${api.api}-${api.type}", result)
            result
        } else
            getLastKnown("isNewBiometricApi-${api.api}-${api.type}")
    }

    @JvmStatic
    fun openSettings(
        activity: Activity, api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    , forced : Boolean = true): Boolean {
        if (!BiometricPromptCompat.isInit)
            return false

        if (BiometricType.BIOMETRIC_ANY != api.type) {
            try {
                Reflection.unseal(activity, Collections.singletonList("org.ifaa.android.manager"))
                //https://git.aicp-rom.com/device_oneplus_oneplus3.git/tree/org.ifaa.android.manager/src/org/ifaa/android/manager/IFAAManagerFactory.java?h=refs/changes/03/28003/1
                //https://github.com/shivatejapeddi/android_device_xiaomi_sdm845-common/tree/10.x-vendor/org.ifaa.android.manager/src/org/ifaa/android/manager
                val authType = when (api.type) {
                    BiometricType.BIOMETRIC_FINGERPRINT -> BiometricAuthenticator.TYPE_FINGERPRINT
                    BiometricType.BIOMETRIC_IRIS -> BiometricAuthenticator.TYPE_IRIS
                    BiometricType.BIOMETRIC_FACE -> BiometricAuthenticator.TYPE_FACE
                    else -> BiometricAuthenticator.TYPE_NONE
                }
                val ifaamanager = IFAAManagerFactory.getIFAAManager(
                    activity,
                    authType
                )
                BiometricLoggerImpl.e("IFAA details: ${ifaamanager.deviceModel}/${ifaamanager.version}")

                if (ifaamanager.startBIOManager(activity, authType) == 0
                ) {
                    return true
                }
            } catch (ignore: Throwable) {
            }

            if (BiometricAuthentication.openSettings(
                    activity,
                    api.type
                )
            )
                return true

            if (forced)
                return Utils.startActivity(
                    Intent(Settings.ACTION_SETTINGS), activity
                )
        } else
            if (BiometricType.BIOMETRIC_ANY == api.type) {
                //for unknown reasons on some devices happens SecurityException - "Permission.MANAGE_BIOMETRIC required" - but not should be
                if (Utils.startActivity(Intent("android.settings.BIOMETRIC_ENROLL"), activity)) {
                    return true
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
                    return true
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
                    return true
                }
                return Utils.startActivity(
                    Intent(Settings.ACTION_SETTINGS), activity
                )
            }
        return false
    }


}