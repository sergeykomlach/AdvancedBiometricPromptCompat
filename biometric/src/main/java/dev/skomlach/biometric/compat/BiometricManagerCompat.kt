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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.SensorPrivacyCheck
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.SettingsHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.permissionui.PermissionsFragment
import dev.skomlach.common.storage.SharedPreferenceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BiometricManagerCompat {
    private const val TAG = "BiometricManagerCompat"
    private val preferences =
        SharedPreferenceProvider.getPreferences("BiometricCompat_ManagerCompat")

    suspend fun loadNonHardwareBiometrics(): Boolean = withContext(Dispatchers.IO) {
        BiometricAuthentication.loadCustomModules()
        return@withContext BiometricAuthentication.customBiometricManagers.isNotEmpty()
    }

    suspend fun unloadNonHardwareBiometrics(): Boolean = withContext(Dispatchers.IO) {
        BiometricAuthentication.unloadCustomModules()
        return@withContext BiometricAuthentication.customBiometricManagers.isEmpty()
    }

    @JvmStatic
    fun isDeviceSecureAvailable(): Boolean {
        val context = AndroidContext.appContext
        val keyguardManager =
            context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)) {
                keyguardManager?.isDeviceSecure == true || keyguardManager?.isKeyguardSecure == true
            } else keyguardManager?.isKeyguardSecure == true
        } else {
            return keyguardManager?.isDeviceSecure == true || keyguardManager?.isKeyguardSecure == true
        }
    }

    @JvmStatic
    fun shouldFallbackToDeviceCredentials(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        if (isBiometricReadyForUsage(api) || isBiometricReadyForEnroll(api))
            return false

        return isBiometricAvailable(api) && isDeviceSecureAvailable()
    }


    @Deprecated("Due to restrictions, this method does not guarantee proper work")
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

    @Deprecated("Due to restrictions, this method does not guarantee proper work")
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
        preferences.edit {
            putBoolean("isBiometricEnrollChanged-${api.api}-${api.type}", result)
        }
        return result
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
                for (type in BiometricType.entries) {
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
                    for (type in BiometricType.entries) {
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


    @JvmStatic
    fun isBiometricAvailable(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        val isHardwareDetected = isHardwareDetected(api)
        val hasEnrolled = hasEnrolled(api)
        return isHardwareDetected && hasEnrolled
    }

    @JvmStatic
    fun isBiometricReadyForUsage(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        return isBiometricAvailable(api) &&
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
            for (s in BiometricType.entries) {
                val v = BiometricAuthRequest(
                    BiometricApi.AUTO,
                    s
                )
                if (isBiometricAvailable(v)) {
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
        val isBiometricAppEnabled = isBiometricAppEnabled()
        BiometricLoggerImpl.d("BiometricManagerCompat.isHardwareDetected for $api return $result isBiometricAppEnabled $isBiometricAppEnabled")
        preferences.edit { putBoolean("isHardwareDetected-${api.api}-${api.type}", result) }
        return result && isBiometricAppEnabled
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
        preferences.edit { putBoolean("hasEnrolled-${api.api}-${api.type}", result) }
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
        else {
            var isLockedOut = HardwareAccessImpl.getInstance(
                BiometricAuthRequest(
                    BiometricApi.LEGACY_API,
                    api.type
                )
            ).isLockedOut
            if (!isLockedOut)
                isLockedOut = HardwareAccessImpl.getInstance(
                    BiometricAuthRequest(
                        BiometricApi.BIOMETRIC_API,
                        api.type
                    )
                ).isLockedOut
            isLockedOut
        }
        val cameraInUse = isCameraInUse(api, ignoreCameraCheck)
        BiometricLoggerImpl.d("BiometricManagerCompat.isLockOut for $api return $result  && $cameraInUse")
        preferences.edit { putBoolean("isLockOut-${api.api}-${api.type}", result) }
        return result || cameraInUse
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

    @JvmStatic
    fun getUsedPermissions(
        types: Collection<BiometricType>
    ): List<String> {

        val permission: MutableSet<String> = java.util.HashSet()
        types.forEach {
            if (Build.VERSION.SDK_INT >= 28 && (it == BiometricType.BIOMETRIC_ANY || it == BiometricType.BIOMETRIC_FINGERPRINT)) {
                permission.add("android.permission.USE_BIOMETRIC")
            } else {
                for (m in BiometricAuthentication.availableBiometricMethods) {
                    if (it == m.biometricType) {
                        when (m) {
                            BiometricMethod.DUMMY_BIOMETRIC -> permission.add("android.permission.CAMERA")
                            BiometricMethod.IRIS_ANDROIDAPI -> permission.add("android.permission.USE_IRIS")
                            BiometricMethod.IRIS_SAMSUNG -> permission.add("com.samsung.android.camera.iris.permission.USE_IRIS")
                            BiometricMethod.FACELOCK -> permission.add("android.permission.WAKE_LOCK")

                            BiometricMethod.FACE_HIHONOR, BiometricMethod.FACE_HIHONOR3D -> permission.add(
                                "com.hihonor.permission.USE_FACERECOGNITION"
                            )

                            BiometricMethod.FACE_HUAWEI, BiometricMethod.FACE_HUAWEI3D -> permission.add(
                                "com.huawei.permission.USE_FACERECOGNITION"
                            )

                            BiometricMethod.FACE_SOTERAPI -> permission.add("android.permission.USE_FACERECOGNITION")

                            BiometricMethod.FACE_ANDROIDAPI -> permission.add("android.permission.USE_FACE_AUTHENTICATION")
                            BiometricMethod.FACE_SAMSUNG -> permission.add("com.samsung.android.bio.face.permission.USE_FACE")
                            BiometricMethod.FACE_OPPO -> permission.add("oppo.permission.USE_FACE")
                            BiometricMethod.FINGERPRINT_API23, BiometricMethod.FINGERPRINT_SUPPORT -> permission.add(
                                "android.permission.USE_FINGERPRINT"
                            )

                            else -> {
                                //no-op
                            }
                        }
                    }
                }
                BiometricAuthentication.customBiometricManagers.forEach { customBiometricManager ->
                    if (it == customBiometricManager.biometricType)
                        permission.addAll(customBiometricManager.getPermissions())
                }
            }
        }

        return ArrayList(permission)
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
        if (getUsedPermissions(listOf(biometricAuthRequest.type)).contains(Manifest.permission.CAMERA)) {
            return SensorPrivacyCheck.isCameraBlocked()
        } else if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
            val types = HashSet<BiometricType>()
            for (type in BiometricType.entries) {
                if (biometricAuthRequest.api == BiometricApi.AUTO || biometricAuthRequest.api == BiometricApi.BIOMETRIC_API) {
                    val request = BiometricAuthRequest(
                        BiometricApi.BIOMETRIC_API,
                        type
                    )
                    if (isBiometricAvailable(request)) {
                        types.add(type)
                    }
                }
                if (biometricAuthRequest.api == BiometricApi.AUTO || biometricAuthRequest.api == BiometricApi.LEGACY_API) {
                    val request = BiometricAuthRequest(
                        BiometricApi.LEGACY_API,
                        type
                    )
                    if (isBiometricAvailable(request)) {
                        types.add(type)
                    }
                }
            }


            if (getUsedPermissions(types).contains(Manifest.permission.CAMERA) &&
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
        if (getUsedPermissions(listOf(biometricAuthRequest.type)).contains(Manifest.permission.CAMERA)) {
            return SensorPrivacyCheck.isCameraInUse()
        } else if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
            val types = HashSet<BiometricType>()

            for (type in BiometricType.entries) {
                if (biometricAuthRequest.api == BiometricApi.AUTO || biometricAuthRequest.api == BiometricApi.BIOMETRIC_API) {
                    val request = BiometricAuthRequest(
                        BiometricApi.BIOMETRIC_API,
                        type
                    )
                    if (isBiometricAvailable(request)) {
                        types.add(type)
                    }
                }
                if (biometricAuthRequest.api == BiometricApi.AUTO || biometricAuthRequest.api == BiometricApi.LEGACY_API) {
                    val request = BiometricAuthRequest(
                        BiometricApi.LEGACY_API,
                        type
                    )
                    if (isBiometricAvailable(request)) {
                        types.add(type)
                    }
                }
            }


            return getUsedPermissions(types).contains(Manifest.permission.CAMERA) &&
                    SensorPrivacyCheck.isCameraInUse()
        }
        return false
    }

    //Special case for Pixel and probable others -
    //user need to enable "Identity verification in apps" feature in device settings
    //NOTE: On newer AOS14 builds this case already handled properly
    private var isBiometricAppEnabledCache = Pair<Long, Boolean>(0, false)

    @SuppressLint("Range")
    private fun isBiometricAppEnabled(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - isBiometricAppEnabledCache.first <= 5_000) {
            return isBiometricAppEnabledCache.second
        }

        val contentResolver = AndroidContext.appContext.contentResolver
        var isEnabled = true

        try {
            val directValue = Settings.Secure.getInt(contentResolver, "biometric_app_enabled", -1)
            if (directValue == 0) {
                isEnabled = false
            } else if (directValue == -1) {
                contentResolver.query(Settings.Secure.CONTENT_URI, null, null, null, null)
                    ?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(Settings.Secure.NAME)
                        val valueIndex = cursor.getColumnIndex(Settings.Secure.VALUE)

                        if (nameIndex != -1 && valueIndex != -1) {
                            while (cursor.moveToNext()) {
                                val key = cursor.getString(nameIndex)?.lowercase() ?: continue
                                if (key == "biometric_app_enabled" ||
                                    (key.startsWith("biometric_") && key.endsWith("_enabled"))
                                ) {
                                    val valueStr = cursor.getString(valueIndex)
                                    val value = valueStr?.toIntOrNull() ?: 1

                                    if (value == 0) {
                                        BiometricLoggerImpl.d(
                                            "BiometricCheck",
                                            "Blocked by key: $key = ${0}"
                                        )
                                        isEnabled = false
                                        break
                                    }
                                }
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            BiometricLoggerImpl.e("BiometricCheck", "Error checking biometric settings", e)
            isEnabled = true
        }
        isBiometricAppEnabledCache = Pair(currentTime, isEnabled)
        return isEnabled
    }

}