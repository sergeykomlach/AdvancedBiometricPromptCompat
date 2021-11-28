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
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.SensorPrivacyCheck
import dev.skomlach.biometric.compat.utils.device.DeviceInfoManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider
import dev.skomlach.common.misc.Utils

object BiometricManagerCompat {

    private val preferences = SharedPreferenceProvider.getCryptoPreferences("BiometricManagerCache")
    @JvmStatic
    fun isBiometricReady(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        return isHardwareDetected(api) && hasEnrolled(api) &&
                !isLockOut(api) && !isBiometricSensorPermanentlyLocked(api)
    }
    @JvmStatic
    fun isBiometricSensorPermanentlyLocked(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        if (!BiometricPromptCompat.API_ENABLED)
            return false
        var result = true
        if (api.api != BiometricApi.AUTO)
            result = BiometricErrorLockoutPermanentFix.isBiometricSensorPermanentlyLocked(api.type)
        else {
            for (s in BiometricType.values()) {
                if (!BiometricErrorLockoutPermanentFix.isBiometricSensorPermanentlyLocked(s)) {
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
        if (!BiometricPromptCompat.API_ENABLED)
            return false
        if (!BiometricPromptCompat.isInit) {
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return preferences.getBoolean("isHardwareDetected-${api.api}-${api.type}", false)
        }
        val result = if (api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isHardwareAvailable
        else
            HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.BIOMETRIC_API,
                    api.type
                )
            ).isHardwareAvailable || HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.LEGACY_API,
                    api.type
                )
            ).isHardwareAvailable

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
        if (!BiometricPromptCompat.API_ENABLED)
            return false
        if (!BiometricPromptCompat.isInit) {
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return preferences.getBoolean("hasEnrolled-${api.api}-${api.type}", false)
        }
        val result = if (api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isBiometricEnrolled
        else
            HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.BIOMETRIC_API,
                    api.type
                )
            ).isBiometricEnrolled || HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.LEGACY_API,
                    api.type
                )
            ).isBiometricEnrolled

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
        if (!BiometricPromptCompat.API_ENABLED)
            return false
        BiometricLoggerImpl.e("NOTE!!! Be careful using 'isBiometricEnrollChanged' - due to technical limitations, it can return incorrect result in many cases")
        if (!BiometricPromptCompat.isInit) {
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return preferences.getBoolean("isBiometricEnrollChanged-${api.api}-${api.type}", false)
        }
        val result = if (api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isBiometricEnrollChanged
        else
            HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.BIOMETRIC_API,
                    api.type
                )
            ).isBiometricEnrollChanged || HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.LEGACY_API,
                    api.type
                )
            ).isBiometricEnrollChanged

        preferences.edit().putBoolean("isBiometricEnrollChanged-${api.api}-${api.type}", result)
            .apply()
        return result
    }

    @JvmStatic
    fun isLockOut(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        if (!BiometricPromptCompat.API_ENABLED)
            return false
        if (!BiometricPromptCompat.isInit) {
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return preferences.getBoolean("isLockOut-${api.api}-${api.type}", false)
        }
        val result = if (api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isLockedOut
        else
            HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.BIOMETRIC_API,
                    api.type
                )
            ).isLockedOut && HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.LEGACY_API,
                    api.type
                )
            ).isLockedOut

        preferences.edit().putBoolean("isLockOut-${api.api}-${api.type}", result).apply()
        return result
    }

    @JvmStatic
    fun openSettings(
        activity: Activity, api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        ), forced: Boolean = true
    ): Boolean {
        if (!BiometricPromptCompat.API_ENABLED)
            return false
        if ((api.type == BiometricType.BIOMETRIC_FACE || (api.type == BiometricType.BIOMETRIC_ANY && DeviceInfoManager.hasFaceID(
                BiometricPromptCompat.deviceInfo
            ))) &&
            SensorPrivacyCheck.isCameraBlocked()
        ) {
            return false
        } else if (api.type == BiometricType.BIOMETRIC_VOICE &&
            SensorPrivacyCheck.isMicrophoneBlocked()
        ) {
            return false
        }
        if (BiometricType.BIOMETRIC_ANY != api.type && BiometricPromptCompat.isInit && BiometricAuthentication.openSettings(
                activity,
                api.type
            )
        ) {
            return true
        }

        if (BiometricType.BIOMETRIC_ANY == api.type || forced) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                enrollIntent.putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                if (Utils.startActivity(enrollIntent, activity))
                    return true
            }
            return Utils.startActivity(
                Intent(Settings.ACTION_SETTINGS), activity
            )
        }
        return false
    }


}