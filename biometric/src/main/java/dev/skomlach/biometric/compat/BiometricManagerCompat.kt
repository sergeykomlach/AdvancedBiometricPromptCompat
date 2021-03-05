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
import dev.skomlach.common.misc.Utils
import me.weishu.reflection.Reflection
import org.ifaa.android.manager.IFAAManagerFactory
import java.util.*

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
        return if(api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isHardwareAvailable
        else
            HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.BIOMETRIC_API, api.type)).isHardwareAvailable || HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.LEGACY_API, api.type)).isHardwareAvailable
    }
    @JvmStatic
    fun hasEnrolled(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        check(BiometricPromptCompat.isInit) { "Please call BiometricPromptCompat.init(null);  first" }
        return if(api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isBiometricEnrolled
        else
            HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.BIOMETRIC_API, api.type)).isBiometricEnrolled || HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.LEGACY_API, api.type)).isBiometricEnrolled

    }
    @JvmStatic
    fun isLockOut(
        api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    ): Boolean {
        check(BiometricPromptCompat.isInit) { "Please call BiometricPromptCompat.init(null);  first" }
        return if(api.api != BiometricApi.AUTO)
            HardwareAccessImpl.getInstance(api).isLockedOut
        else
            HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.BIOMETRIC_API, api.type)).isLockedOut || HardwareAccessImpl.getInstance(BiometricAuthRequest(BiometricApi.LEGACY_API, api.type)).isLockedOut

    }

    @JvmStatic
    fun openSettings(
        activity: Activity, api: BiometricAuthRequest = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_ANY
        )
    , forced : Boolean = true): Boolean {
        check(BiometricPromptCompat.isInit) { "Please call BiometricPromptCompat.init(null);  first" }

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
                BiometricLoggerImpl.e("IFAA details: ${ifaamanager?.deviceModel}/${ifaamanager?.version}")

                if (ifaamanager?.startBIOManager(activity, authType) == 0
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