/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.custom.CustomBiometricProvider
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.SensorPrivacyCheck
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.multiwindow.MultiWindowSupport
import dev.skomlach.common.permissionui.PermissionsFragment
import dev.skomlach.common.storage.SharedPreferenceProvider

object BiometricManagerCompat {
    private val preferences =
        SharedPreferenceProvider.getPreferences("BiometricCompat_ManagerCompat")

    @JvmStatic
    fun isDeviceSecureAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val keyguardManager =
            context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return keyguardManager?.isKeyguardSecure == true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)) {
                keyguardManager?.isDeviceSecure == true
            } else false
        } else {
            return keyguardManager?.isDeviceSecure == true
        }
    }

    @JvmStatic
    fun getUsedPermissions(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): List<String> {

        val permission: MutableSet<String> = java.util.HashSet()

        if (Build.VERSION.SDK_INT >= 28) {
            permission.add("android.permission.USE_BIOMETRIC")
        }

        val biometricMethodList: MutableList<BiometricMethod> = ArrayList()
        for (m in BiometricAuthentication.availableBiometricMethods) {
            if (api.type == BiometricType.BIOMETRIC_ANY || api.type == m.biometricType) {
                biometricMethodList.add(m)
            }
        }
        for (method in biometricMethodList) {
            when (method) {
                BiometricMethod.DUMMY_BIOMETRIC -> permission.add("android.permission.CAMERA")
                BiometricMethod.IRIS_ANDROIDAPI -> permission.add("android.permission.USE_IRIS")
                BiometricMethod.IRIS_SAMSUNG -> permission.add("com.samsung.android.camera.iris.permission.USE_IRIS")
                BiometricMethod.FACELOCK -> permission.add("android.permission.WAKE_LOCK")
                BiometricMethod.FACE_HUAWEI, BiometricMethod.FACE_HUAWEI3D, BiometricMethod.FACE_SOTERAPI -> permission.add(
                    "android.permission.USE_FACERECOGNITION"
                )

                BiometricMethod.FACE_HIHONOR, BiometricMethod.FACE_HIHONOR3D -> permission.add("android.permission.CAMERA")
                BiometricMethod.FACE_ANDROIDAPI -> permission.add("android.permission.USE_FACE_AUTHENTICATION")
                BiometricMethod.FACE_SAMSUNG -> permission.add("com.samsung.android.bio.face.permission.USE_FACE")
                BiometricMethod.FACE_OPPO -> permission.add("oppo.permission.USE_FACE")
                BiometricMethod.FINGERPRINT_API23, BiometricMethod.FINGERPRINT_SUPPORT -> permission.add(
                    "android.permission.USE_FINGERPRINT"
                )

                BiometricMethod.FINGERPRINT_FLYME -> permission.add("com.fingerprints.service.ACCESS_FINGERPRINT_MANAGER")
                BiometricMethod.FINGERPRINT_SAMSUNG -> permission.add("com.samsung.android.providers.context.permission.WRITE_USE_APP_FEATURE_SURVEY")
                else -> {
                    //no-op
                }
            }
        }

        return ArrayList(permission)
    }

    @JvmStatic
    fun resetBiometricEnrollChanged(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ) {
        if (api.api != BiometricApi.AUTO) {
            HardwareAccessImpl.getInstance(api)
                .updateBiometricEnrollChanged()
        } else {
            HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.BIOMETRIC_API,
                    api.type
                )
            )
                .updateBiometricEnrollChanged()
            HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.LEGACY_API,
                    api.type
                )
            )
                .updateBiometricEnrollChanged()
        }

        isBiometricEnrollChanged(api)
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
        if (!BiometricPromptCompat.isInitialized) {
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return preferences.getBoolean("isBiometricEnrollChanged-${api.api}-${api.type}", false)
        }
        val result = if (api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isBiometricEnrollChanged
        else
            HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.LEGACY_API,
                    api.type
                )
            ).isBiometricEnrollChanged || HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.BIOMETRIC_API,
                    api.type
                )
            ).isBiometricEnrollChanged
        BiometricLoggerImpl.d("BiometricManagerCompat.isBiometricEnrollChanged for $api return $result")
        preferences.edit().putBoolean("isBiometricEnrollChanged-${api.api}-${api.type}", result)
            .apply()
        return result
    }

    @JvmStatic
    fun registerCustomBiometric(
        id: Int,
        type: BiometricType,
        provider: CustomBiometricProvider
    ): Boolean {
        if (!BiometricPromptCompat.API_ENABLED)
            return false

        return BiometricAuthentication.registerCustomModule(
            BiometricMethod.createCustomModule(
                id,
                type
            ), provider
        )
    }

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
                    if (isBiometricReadyForUsage(request)) {
                        types.add(type)
                    }
                }
            } else {
                if (isBiometricReadyForUsage(biometricAuthRequest))
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
                        if (isBiometricReadyForUsage(request)) {
                            types.add(type)
                        }
                    }
                } else {
                    if (isBiometricReadyForUsage(biometricAuthRequest))
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
                    && DevicesWithKnownBugs.hasUnderDisplayFingerprint)
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
    fun isBiometricAvailable(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        return isHardwareDetected(api) && hasEnrolled(api)
    }

    @JvmStatic
    fun isBiometricReadyForUsage(
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
        BiometricLoggerImpl.d("BiometricManagerCompat.isBiometricSensorPermanentlyLocked for $api return ${result || isCameraBlocked}")
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
        if (!BiometricPromptCompat.isInitialized) {
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return preferences.getBoolean("isHardwareDetected-${api.api}-${api.type}", false)
        }
        val result = if (api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isHardwareAvailable
        else
            HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.LEGACY_API,
                    api.type
                )
            ).isHardwareAvailable || HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.BIOMETRIC_API,
                    api.type
                )
            ).isHardwareAvailable
        BiometricLoggerImpl.d("BiometricManagerCompat.isHardwareDetected for $api return $result")
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
        if (!BiometricPromptCompat.isInitialized) {
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return preferences.getBoolean("hasEnrolled-${api.api}-${api.type}", false)
        }
        val result = if (api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isBiometricEnrolled
        else
            HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.LEGACY_API,
                    api.type
                )
            ).isBiometricEnrolled || HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.BIOMETRIC_API,
                    api.type
                )
            ).isBiometricEnrolled
        BiometricLoggerImpl.d("BiometricManagerCompat.hasEnrolled for $api return $result")
        preferences.edit().putBoolean("hasEnrolled-${api.api}-${api.type}", result).apply()
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
        if (!BiometricPromptCompat.isInitialized) {
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
                    BiometricApi.LEGACY_API,
                    api.type
                )
            ).isLockedOut && HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.BIOMETRIC_API,
                    api.type
                )
            ).isLockedOut
        val cameraInUse = isCameraInUse(api, ignoreCameraCheck)
        BiometricLoggerImpl.d("BiometricManagerCompat.isLockOut for $api return ${result || cameraInUse}")
        preferences.edit().putBoolean("isLockOut-${api.api}-${api.type}", result).apply()
        return result || cameraInUse
    }

    @JvmStatic
    fun requestPermissions(
        activity: FragmentActivity,
        usedPermissions: List<String>,
        onComplete: Runnable? = null
    ) {
        PermissionsFragment.askForPermissions(
            activity,
            usedPermissions,
            onComplete
        )
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

        if (BiometricType.BIOMETRIC_ANY != api.type && BiometricPromptCompat.isInitialized && BiometricAuthentication.openSettings(
                activity,
                api.type
            )
        ) {
            return true
        }

        if (BiometricType.BIOMETRIC_ANY == api.type || forced) {
            return Utils.startActivity(
                Intent(Settings.ACTION_SETTINGS), activity
            )
        }
        return false
    }
}