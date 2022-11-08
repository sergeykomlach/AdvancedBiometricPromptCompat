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
import android.provider.Settings
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.SensorPrivacyCheck
import dev.skomlach.biometric.compat.utils.device.DeviceInfoManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.storage.SharedPreferenceProvider

object BiometricManagerCompat {

    private val preferences =
        SharedPreferenceProvider.getPreferences("BiometricCompat_ManagerCompat")
    @JvmStatic
    fun isSilentAuthAvailable(
        biometricAuthRequest: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        val primaryAvailableTypes: java.util.HashSet<BiometricType> by lazy {
            val types = java.util.HashSet<BiometricType>()
            val api =
                if (HardwareAccessImpl.getInstance(biometricAuthRequest).isNewBiometricApi) BiometricApi.BIOMETRIC_API else BiometricApi.LEGACY_API
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                for (type in BiometricType.values()) {
                    if (type == BiometricType.BIOMETRIC_ANY)
                        continue
                    val request = BiometricAuthRequest(
                        api,
                        type
                    )
                    if (isBiometricReady(request)) {
                        types.add(type)
                    }
                }
            } else {
                if (isBiometricReady(biometricAuthRequest))
                    types.add(biometricAuthRequest.type)
            }
            types
        }
        val secondaryAvailableTypes: java.util.HashSet<BiometricType> by lazy {
            val types = java.util.HashSet<BiometricType>()
            if (HardwareAccessImpl.getInstance(biometricAuthRequest).isNewBiometricApi) {
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                    for (type in BiometricType.values()) {
                        if (type == BiometricType.BIOMETRIC_ANY)
                            continue
                        val request = BiometricAuthRequest(
                            BiometricApi.LEGACY_API,
                            type
                        )
                        if (isBiometricReady(request)) {
                            types.add(type)
                        }
                    }
                } else {
                    if (isBiometricReady(biometricAuthRequest))
                        types.add(biometricAuthRequest.type)
                }
                types.removeAll(primaryAvailableTypes)
            }
            types
        }
        val allAvailableTypes: java.util.HashSet<BiometricType> by lazy {
            val types = java.util.HashSet<BiometricType>()
            types.addAll(primaryAvailableTypes)
            types.addAll(secondaryAvailableTypes)
            types
        }
        val list = allAvailableTypes.filter {
            !((it == BiometricType.BIOMETRIC_FINGERPRINT || it == BiometricType.BIOMETRIC_ANY)
                    && DeviceInfoManager.hasUnderDisplayFingerprint(BiometricPromptCompat.deviceInfo))
        }

        return list.isNotEmpty()

    }
    private fun isCameraNotAvailable(
        biometricAuthRequest: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        ),
        ignoreCameraCheck: Boolean
    ): Boolean {
        if (ignoreCameraCheck)
            return false
        if (biometricAuthRequest.type == BiometricType.BIOMETRIC_FACE) {
            return SensorPrivacyCheck.isCameraBlocked()
        } else if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
            val types = HashSet<BiometricType>()
            for (type in BiometricType.values()) {
                if (biometricAuthRequest.api == BiometricApi.AUTO || biometricAuthRequest.api == BiometricApi.BIOMETRIC_API) {
                    val request = BiometricAuthRequest(
                        BiometricApi.BIOMETRIC_API,
                        type
                    )
                    if (isHardwareDetected(request)) {
                        types.add(type)
                    }
                }
                if (biometricAuthRequest.api == BiometricApi.AUTO || biometricAuthRequest.api == BiometricApi.LEGACY_API) {
                    val request = BiometricAuthRequest(
                        BiometricApi.LEGACY_API,
                        type
                    )
                    if (isHardwareDetected(request)) {
                        types.add(type)
                    }
                }
            }


            if (types.contains(BiometricType.BIOMETRIC_FACE) &&
                SensorPrivacyCheck.isCameraBlocked()
            )
                return true
        }
        return false
    }

    private fun isCameraInUse(
        biometricAuthRequest: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        ),
        ignoreCameraCheck: Boolean
    ): Boolean {
        if (ignoreCameraCheck)
            return false
        if (biometricAuthRequest.type == BiometricType.BIOMETRIC_FACE) {
            return SensorPrivacyCheck.isCameraInUse()
        } else if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
            val types = HashSet<BiometricType>()

            for (type in BiometricType.values()) {
                if (biometricAuthRequest.api == BiometricApi.AUTO || biometricAuthRequest.api == BiometricApi.BIOMETRIC_API) {
                    val request = BiometricAuthRequest(
                        BiometricApi.BIOMETRIC_API,
                        type
                    )
                    if (isHardwareDetected(request)) {
                        types.add(type)
                    }
                }
                if (biometricAuthRequest.api == BiometricApi.AUTO || biometricAuthRequest.api == BiometricApi.LEGACY_API) {
                    val request = BiometricAuthRequest(
                        BiometricApi.LEGACY_API,
                        type
                    )
                    if (isHardwareDetected(request)) {
                        types.add(type)
                    }
                }
            }


            return types.contains(BiometricType.BIOMETRIC_FACE) &&
                    SensorPrivacyCheck.isCameraInUse()
        }
        return false
    }

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
    fun isBiometricReadyForEnroll(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        return isHardwareDetected(api) &&
                !isLockOut(api) && !isBiometricSensorPermanentlyLocked(api)
    }

    @JvmStatic
    fun isBiometricSensorPermanentlyLocked(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        ),
        ignoreCameraCheck: Boolean = true
    ): Boolean {
        if (!BiometricPromptCompat.API_ENABLED)
            return false
        var result = false
        if (api.api != BiometricApi.AUTO)
            result = BiometricErrorLockoutPermanentFix.isBiometricSensorPermanentlyLocked(api.type)
        else {
            var total = 0
            var counted = 0
            for (s in BiometricType.values()) {
                val v = BiometricAuthRequest(
                    BiometricApi.AUTO,
                    s
                )
                if (isHardwareDetected(v) && hasEnrolled(v)) {
                    total++
                    if (BiometricErrorLockoutPermanentFix.isBiometricSensorPermanentlyLocked(s)) {
                        counted++
                    }
                }
            }
            result = total > 0 && (total == counted)
        }
        val isCameraBlocked = isCameraNotAvailable(api, ignoreCameraCheck)
        BiometricLoggerImpl.d("BiometricPromptManager. isBiometricSensorPermanentlyLocked - result=$result; isCameraBlocked=$isCameraBlocked")
        return result || isCameraBlocked
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
        ),
        ignoreCameraCheck: Boolean = true
    ): Boolean {
        if (!BiometricPromptCompat.API_ENABLED)
            return false
        if (!BiometricPromptCompat.isInit) {
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return isCameraInUse(api, ignoreCameraCheck) || preferences.getBoolean(
                "isLockOut-${api.api}-${api.type}",
                false
            )
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
        return result || isCameraInUse(api, ignoreCameraCheck)
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

        if (!isHardwareDetected(api) || isLockOut(api) || isBiometricSensorPermanentlyLocked(api))//Enroll cann't be started till access blocked
            return false

        if (BiometricType.BIOMETRIC_ANY != api.type && BiometricPromptCompat.isInit && BiometricAuthentication.openSettings(
                activity,
                api.type
            )
        ) {
            return true
        }

        if (BiometricType.BIOMETRIC_ANY == api.type || forced) {
            if (Utils.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS),
                    activity
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