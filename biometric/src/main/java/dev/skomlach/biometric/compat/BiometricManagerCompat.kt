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
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.LegacyBiometric
import dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricModule
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.SensorPrivacyCheck
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.storage.SharedPreferenceProvider

object BiometricManagerCompat {
    private const val TAG = "BiometricManagerCompat"
    private val preferences =
        SharedPreferenceProvider.getPreferences("BiometricCompat_ManagerCompat")

    fun loadNonHardwareBiometrics() {
        LegacyBiometric.loadSoftwareModules()
    }

    fun unregisterAllNonHardwareBiometrics() {
        LegacyBiometric.unregisterAllNonHardwareBiometrics()
    }

    fun unloadNonHardwareBiometrics() {
        LegacyBiometric.unloadSoftwareModules()
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
        api: BiometricAuthRequest = BiometricAuthRequest.default()
    ): Boolean {
        val snapshot = getAuthSnapshot(api)
        if (snapshot.readyForEnroll)
            return false

        return snapshot.available && isDeviceSecureAvailable()
    }


    @Deprecated("Due to restrictions, this method does not guarantee proper work")
    @JvmStatic
    fun resetBiometricEnrollChanged(
        api: BiometricAuthRequest = BiometricAuthRequest.default()
    ) {
        if (api.api != BiometricApi.AUTO) {
            HardwareAccessImpl.getInstance(api)
                .updateBiometricEnrollChanged()
        } else {
            HardwareAccessImpl.getInstance(
                api.withApi(BiometricApi.BIOMETRIC_API)
            )
                .updateBiometricEnrollChanged()
            HardwareAccessImpl.getInstance(
                api.withApi(BiometricApi.LEGACY_API)
            )
                .updateBiometricEnrollChanged()
        }

        isBiometricEnrollChanged(api)
    }

    @Deprecated("Due to restrictions, this method does not guarantee proper work")
    @JvmStatic
    fun isBiometricEnrollChanged(
        api: BiometricAuthRequest = BiometricAuthRequest.default()
    ): Boolean {
        if (!BiometricPromptCompat.API_ENABLED)
            return false
        BiometricLoggerImpl.e("NOTE!!! Be careful using 'isBiometricEnrollChanged' - due to technical limitations, it can return incorrect result in many cases")
        if (!BiometricPromptCompat.isInitialized) {
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return preferences.getBoolean(api.stateCacheKey("isBiometricEnrollChanged"), false)
        }
        val result = if (api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isBiometricEnrollChanged
        else
            HardwareAccessImpl.getInstance(
                api.withApi(BiometricApi.LEGACY_API)
            ).isBiometricEnrollChanged || HardwareAccessImpl.getInstance(
                api.withApi(BiometricApi.BIOMETRIC_API)
            ).isBiometricEnrollChanged
        BiometricLoggerImpl.d("BiometricManagerCompat.isBiometricEnrollChanged for $api return $result")
        preferences.edit {
            putBoolean(api.stateCacheKey("isBiometricEnrollChanged"), result)
        }
        return result
    }

    @JvmStatic
    fun isSilentAuthAvailable(
        biometricAuthRequest: BiometricAuthRequest = BiometricAuthRequest.default()
    ): Boolean {
        val primaryApi =
            if (HardwareAccessImpl.getInstance(biometricAuthRequest).isNewBiometricApi)
                BiometricApi.BIOMETRIC_API
            else
                BiometricApi.LEGACY_API
        val requestedTypes = biometricAuthRequest.expandedTypes()
        val primaryAvailableTypes = requestedTypes.filterTo(HashSet()) { type ->
            getAuthSnapshot(biometricAuthRequest.withApi(primaryApi).withType(type)).readyForEnroll
        }
        val allAvailableTypes = HashSet(primaryAvailableTypes)
        if (primaryApi == BiometricApi.BIOMETRIC_API) {
            requestedTypes.filterTo(allAvailableTypes) { type ->
                !primaryAvailableTypes.contains(type) &&
                        getAuthSnapshot(
                            biometricAuthRequest.withApi(BiometricApi.LEGACY_API).withType(type)
                        ).readyForEnroll
            }
        }
        val list = allAvailableTypes.filter {
            !((it == BiometricType.BIOMETRIC_FINGERPRINT || it == BiometricType.BIOMETRIC_ANY)
                    && DevicesWithKnownBugs.hasUnderDisplayFingerprint)
        }

        return list.isNotEmpty()

    }


    @JvmStatic
    fun isBiometricAvailable(
        api: BiometricAuthRequest = BiometricAuthRequest.default()
    ): Boolean {
        return getAuthSnapshot(api).available
    }

    @JvmStatic
    fun isBiometricReadyForUsage(
        api: BiometricAuthRequest = BiometricAuthRequest.default()
    ): Boolean {
        return getAuthSnapshot(api).readyForUsage
    }

    @JvmStatic
    fun isBiometricReadyForEnroll(
        api: BiometricAuthRequest = BiometricAuthRequest.default()
    ): Boolean {
        return getAuthSnapshot(api).readyForEnroll
    }

    @JvmStatic
    fun isBiometricSensorPermanentlyLocked(
        api: BiometricAuthRequest = BiometricAuthRequest.default(),
        ignoreCameraCheck: Boolean = true
    ): Boolean {
        val result = getAuthSnapshot(api, ignoreCameraCheck).state.permanentlyLocked
        BiometricLoggerImpl.d("BiometricManagerCompat.isBiometricSensorPermanentlyLocked for $api return $result")
        return result
    }

    @JvmStatic
    fun isHardwareDetected(
        api: BiometricAuthRequest = BiometricAuthRequest.default()
    ): Boolean {
        val result = getAuthSnapshot(api).state.hardwareDetected
        BiometricLoggerImpl.d("BiometricManagerCompat.isHardwareDetected for $api return $result")
        return result
    }

    @JvmStatic
    fun hasEnrolled(
        api: BiometricAuthRequest = BiometricAuthRequest.default()
    ): Boolean {
        val result = getAuthSnapshot(api).state.enrolled
        BiometricLoggerImpl.d("BiometricManagerCompat.hasEnrolled for $api return $result")
        return result
    }

    @JvmStatic
    fun isLockOut(
        api: BiometricAuthRequest = BiometricAuthRequest.default(),
        ignoreCameraCheck: Boolean = true
    ): Boolean {
        val result = getAuthSnapshot(api, ignoreCameraCheck).state.lockedOut
        BiometricLoggerImpl.d("BiometricManagerCompat.isLockOut for $api return $result")
        return result
    }

    @JvmStatic
    fun getAuthSnapshot(
        api: BiometricAuthRequest = BiometricAuthRequest.default(),
        ignoreCameraCheck: Boolean = true
    ): BiometricAuthSnapshot {
        if (!BiometricPromptCompat.API_ENABLED) {
            return BiometricAuthSnapshot(
                request = api,
                routes = emptyList(),
                state = unavailableAuthState()
            )
        }

        if (!BiometricPromptCompat.isInitialized) {
            BiometricLoggerImpl.e("Please call BiometricPromptCompat.init(null);  first")
            return BiometricAuthSnapshot(
                request = api,
                routes = emptyList(),
                state = cachedAuthState(api, ignoreCameraCheck)
            )
        }

        val snapshot = if (api.api == BiometricApi.AUTO) {
            autoAuthSnapshot(api)
        } else {
            directAuthSnapshot(api)
        }.withEnvironmentState(ignoreCameraCheck)

        preferences.edit {
            putBoolean(api.stateCacheKey("isHardwareDetected"), snapshot.state.hardwareDetected)
            putBoolean(api.stateCacheKey("hasEnrolled"), snapshot.state.enrolled)
            putBoolean(api.stateCacheKey("isLockOut"), snapshot.state.lockedOut)
        }
        return snapshot
    }

    private fun cachedAuthState(
        api: BiometricAuthRequest,
        ignoreCameraCheck: Boolean
    ): BiometricAuthState {
        return BiometricAuthState(
            hardwareDetected = preferences.getBoolean(api.stateCacheKey("isHardwareDetected"), false),
            enrolled = preferences.getBoolean(api.stateCacheKey("hasEnrolled"), false),
            lockedOut = preferences.getBoolean(api.stateCacheKey("isLockOut"), false),
            permanentlyLocked = false
        ).withEnvironmentState(api, ignoreCameraCheck)
    }

    private fun directAuthSnapshot(api: BiometricAuthRequest): BiometricAuthSnapshot {
        val route = directAuthRouteState(api)
        return BiometricAuthSnapshot(
            request = api,
            routes = listOf(route),
            state = route.state
        )
    }

    private fun BiometricAuthRequest.expandedTypes(): List<BiometricType> {
        return if (type == BiometricType.BIOMETRIC_ANY) {
            BiometricType.entries.filterNot { it == BiometricType.BIOMETRIC_ANY }
        } else {
            listOf(type)
        }
    }

    private fun directAuthRouteState(api: BiometricAuthRequest): BiometricAuthRouteState {
        val access = HardwareAccessImpl.getInstance(api)
        val baseState = access.authState
        val state = baseState.copy(
            permanentlyLocked = baseState.permanentlyLocked ||
                    BiometricErrorLockoutPermanentFix.isBiometricSensorPermanentlyLocked(api.type)
        )
        return BiometricAuthRouteState(
            request = api,
            source = when (api.api) {
                BiometricApi.BIOMETRIC_API -> BiometricAuthRouteSource.BIOMETRIC_PROMPT
                else -> BiometricAuthRouteSource.LEGACY
            },
            state = state
        )
    }

    private fun autoAuthSnapshot(api: BiometricAuthRequest): BiometricAuthSnapshot {
        if (api.type == BiometricType.BIOMETRIC_ANY) {
            val typedSnapshots = BiometricType.entries
                .asSequence()
                .filterNot { it == BiometricType.BIOMETRIC_ANY }
                .map { autoTypedAuthSnapshot(api.withType(it)) }
                .toList()
            return BiometricAuthSnapshot(
                request = api,
                routes = typedSnapshots.flatMap { it.routes },
                state = aggregateAnyBiometricState(typedSnapshots.map { it.state })
            )
        }
        return autoTypedAuthSnapshot(api)
    }

    private fun autoTypedAuthSnapshot(api: BiometricAuthRequest): BiometricAuthSnapshot {
        val legacyRoute = directAuthRouteState(api.withApi(BiometricApi.LEGACY_API))
        val biometricPromptRoute = directAuthRouteState(api.withApi(BiometricApi.BIOMETRIC_API))
        return BiometricAuthSnapshot(
            request = api,
            routes = listOf(legacyRoute, biometricPromptRoute),
            state = aggregateTypedAutoBiometricState(
                legacyRoute.state,
                biometricPromptRoute.state,
                preferLegacyEnrollment = shouldPreferLegacyEnrollment(api, legacyRoute.state)
            )
        )
    }

    private fun shouldPreferLegacyEnrollment(
        api: BiometricAuthRequest,
        legacyState: BiometricAuthState
    ): Boolean {
        if (api.provider == BiometricProviderType.HARDWARE ||
            api.type == BiometricType.BIOMETRIC_FINGERPRINT ||
            !legacyState.hardwareDetected
        ) {
            return false
        }
        return LegacyBiometric.getAvailableBiometricModules(api.type, api.provider)
            .any { it is SoftwareBiometricModule }
    }

    private fun BiometricAuthState.withEnvironmentState(
        api: BiometricAuthRequest,
        ignoreCameraCheck: Boolean
    ): BiometricAuthState {
        val appEnabled = isBiometricAppEnabled()
        return copy(
            hardwareDetected = hardwareDetected && appEnabled,
            lockedOut = lockedOut || isCameraInUse(api, ignoreCameraCheck),
            permanentlyLocked = permanentlyLocked || isCameraNotAvailable(api, ignoreCameraCheck)
        )
    }

    private fun BiometricAuthSnapshot.withEnvironmentState(
        ignoreCameraCheck: Boolean
    ): BiometricAuthSnapshot {
        return copy(state = state.withEnvironmentState(request, ignoreCameraCheck))
    }

    private fun unavailableAuthState(): BiometricAuthState {
        return BiometricAuthState(
            hardwareDetected = false,
            enrolled = false,
            lockedOut = false,
            permanentlyLocked = false
        )
    }

    @JvmStatic
    fun getUsedPermissions(
        types: Collection<BiometricType>
    ): List<String> {
        val permission = getHardwarePermissions(types)
        permission.addAll(LegacyBiometric.getSoftwareModulePermissions(types))
        return ArrayList(permission)
    }

    internal fun getUsedPermissions(
        types: Collection<BiometricType>,
        biometricAuthRequest: BiometricAuthRequest,
        enroll: Boolean
    ): List<String> {
        val permission: MutableSet<String> = java.util.HashSet()
        if (biometricAuthRequest.provider != BiometricProviderType.SOFTWARE) {
            permission.addAll(getHardwarePermissions(types))
        }

        if (biometricAuthRequest.provider != BiometricProviderType.HARDWARE) {
            permission.addAll(
                if (biometricAuthRequest.provider == BiometricProviderType.SOFTWARE) {
                    LegacyBiometric.getSoftwareModulePermissions(types)
                } else {
                    LegacyBiometric.getPreferredSoftwareModulePermissions(
                        types,
                        biometricAuthRequest.provider,
                        enroll
                    )
                }
            )
        }

        return ArrayList(permission)
    }

    private fun getHardwarePermissions(
        types: Collection<BiometricType>
    ): MutableSet<String> {

        val permission: MutableSet<String> = java.util.HashSet()
        types.forEach {
            if (Build.VERSION.SDK_INT >= 28 && it == BiometricType.BIOMETRIC_ANY) {
                permission.add("android.permission.USE_BIOMETRIC")
            } else {
                for (m in LegacyBiometric.availableBiometricMethods) {
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

            }
        }
        return permission
    }

    private fun isCameraNotAvailable(
        biometricAuthRequest: BiometricAuthRequest = BiometricAuthRequest.default(),
        ignoreCameraCheck: Boolean
    ): Boolean {
        if (ignoreCameraCheck)
            return false
        if (getUsedPermissions(listOf(biometricAuthRequest.type)).contains(Manifest.permission.CAMERA)) {
            return SensorPrivacyCheck.isCameraBlocked()
        } else if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
            val types = availableRouteTypesForCameraCheck(biometricAuthRequest)
            if (getUsedPermissions(types).contains(Manifest.permission.CAMERA) &&
                SensorPrivacyCheck.isCameraBlocked()
            )
                return true
        }
        return false
    }

    private fun isCameraInUse(
        biometricAuthRequest: BiometricAuthRequest = BiometricAuthRequest.default(),
        ignoreCameraCheck: Boolean
    ): Boolean {
        if (ignoreCameraCheck)
            return false
        if (getUsedPermissions(listOf(biometricAuthRequest.type)).contains(Manifest.permission.CAMERA)) {
            return SensorPrivacyCheck.isCameraInUse()
        } else if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
            val types = availableRouteTypesForCameraCheck(biometricAuthRequest)
            return getUsedPermissions(types).contains(Manifest.permission.CAMERA) &&
                    SensorPrivacyCheck.isCameraInUse()
        }
        return false
    }

    private fun availableRouteTypesForCameraCheck(
        biometricAuthRequest: BiometricAuthRequest
    ): Set<BiometricType> {
        return biometricAuthRequest.expandedTypes().filterTo(HashSet()) { type ->
            when (biometricAuthRequest.api) {
                BiometricApi.AUTO -> autoTypedAuthSnapshot(
                    biometricAuthRequest.withType(type)
                ).available

                BiometricApi.BIOMETRIC_API,
                BiometricApi.LEGACY_API -> directAuthSnapshot(
                    biometricAuthRequest.withType(type)
                ).available
            }
        }
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
