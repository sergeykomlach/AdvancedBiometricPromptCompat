/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

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
import org.ifaa.android.manager.IFAAManagerFactory

object BiometricManagerCompat {

    private val preferences = SharedPreferenceProvider.getCryptoPreferences("BiometricManagerCache")
    @JvmStatic
    fun isBiometricSensorPermanentlyLocked(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        var result = true
        if (api.api != BiometricApi.AUTO)
            result = BiometricErrorLockoutPermanentFix.INSTANCE.isBiometricSensorPermanentlyLocked(api.type)
        else {
            for (s in BiometricType.values()) {
                if (!BiometricErrorLockoutPermanentFix.INSTANCE.isBiometricSensorPermanentlyLocked(s)) {
                    result = false
                    break
                }
            }
        }
        return result
    }
    @JvmStatic
    fun isHardwareDetected(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        if(!BiometricPromptCompat.isInit){
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return preferences.getBoolean("isHardwareDetected-${api.api}-${api.type}", false)
        }
        val result = if(api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isHardwareAvailable
        else
            HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.BIOMETRIC_API, api.type)).isHardwareAvailable || HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.LEGACY_API, api.type)).isHardwareAvailable

        preferences.edit().putBoolean("isHardwareDetected-${api.api}-${api.type}", result).apply()
        return result
    }
    @JvmStatic
    fun hasEnrolled(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        if(!BiometricPromptCompat.isInit){
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return preferences.getBoolean("hasEnrolled-${api.api}-${api.type}", false)
        }
        val result = if(api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isBiometricEnrolled
        else
            HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.BIOMETRIC_API, api.type)).isBiometricEnrolled || HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.LEGACY_API, api.type)).isBiometricEnrolled

        preferences.edit().putBoolean("hasEnrolled-${api.api}-${api.type}", result).apply()
        return result
    }
    @JvmStatic
    fun isBiometricEnrollChanged(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        BiometricLoggerImpl.e("NOTE!!! Be careful using 'isBiometricEnrollChanged' - due to technical limitations, it can return incorrect result in many cases")
        if(!BiometricPromptCompat.isInit){
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return preferences.getBoolean("isBiometricEnrollChanged-${api.api}-${api.type}", false)
        }
        val result = if(api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isBiometricEnrollChanged
        else
            HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.BIOMETRIC_API, api.type)).isBiometricEnrollChanged || HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.LEGACY_API, api.type)).isBiometricEnrollChanged

        preferences.edit().putBoolean("isBiometricEnrollChanged-${api.api}-${api.type}", result).apply()
        return result
    }
    @JvmStatic
    fun isLockOut(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        if(!BiometricPromptCompat.isInit){
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return preferences.getBoolean("isLockOut-${api.api}-${api.type}", false)
        }
        val result = if(api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isLockedOut
        else
            HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.BIOMETRIC_API, api.type)).isLockedOut && HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.LEGACY_API, api.type)).isLockedOut

        preferences.edit().putBoolean("isLockOut-${api.api}-${api.type}", result).apply()
        return result
    }

    @JvmStatic
    fun openSettings(
        activity: Activity, api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    , forced : Boolean = true): Boolean {
        if (BiometricType.BIOMETRIC_ANY != api.type) {
            try {
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
                BiometricLoggerImpl.d("IFAA details: ${ifaamanager?.deviceModel}/${ifaamanager?.version}")

                if (ifaamanager?.startBIOManager(activity, authType) == 0
                ) {
                    return true
                }
            } catch (ignore: Throwable) {
            }
            if (BiometricPromptCompat.isInit && BiometricAuthentication.openSettings(
                    activity,
                    api.type
                )
            )
                return true
        }
        if (BiometricType.BIOMETRIC_ANY == api.type || forced) {
            //for unknown reasons on some devices happens SecurityException - "Permission.MANAGE_BIOMETRIC required" - but not should be
            if (Utils.startActivity(Intent("android.settings.BIOMETRIC_ENROLL"), activity)) {
                return true
            }
            return Utils.startActivity(
                Intent(Settings.ACTION_SETTINGS), activity
            )
        }
        return false
    }


}