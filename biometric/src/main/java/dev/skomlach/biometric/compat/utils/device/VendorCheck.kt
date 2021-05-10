/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
package dev.skomlach.biometric.compat.utils.device

import android.os.Build
import android.os.Build.VERSION
import android.text.TextUtils
import dev.skomlach.biometric.compat.utils.SystemPropertiesProxy.get
import dev.skomlach.biometric.compat.utils.device.AppUtils.createIntent
import dev.skomlach.biometric.compat.utils.device.AppUtils.isIntentAvailable
import dev.skomlach.biometric.compat.utils.device.AppUtils.isSystemPkgInstalled
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import java.lang.reflect.Modifier
import java.util.*

object VendorCheck {
    private fun checkForVendor(s: String): Boolean {
        var vendor = s
        vendor = vendor.lowercase(Locale.ROOT)
        val allFields = Build::class.java.fields
        for (f in allFields) try {
            if (!Modifier.isPrivate(f.modifiers) && f.type == String::class.java) {
                val value = f[null] as String
                if (value.lowercase(Locale.ROOT).contains(vendor)) return true
            }
        } catch (e: Throwable) {

        }
        return false
    }

    //Color OS (Oppo)
    val isOppo: Boolean
        get() {
            val OPPO_GUARD_PKG_V1 = "com.color.oppoguardelf"
            val OPPO_SAFE_PKG = "com.oppo.safe"
            val OPPO_GUARD_PKG_V2 = "com.coloros.oppoguardelf"
            val OPPO_SAFECENTRE_PKG = "com.coloros.safecenter"
            val OPPO_SAFECENTRE_PKG_v2 = "com.color.safecenter"

            return isSystemPkgInstalled(appContext, OPPO_SAFECENTRE_PKG)
                    || isSystemPkgInstalled(appContext, OPPO_SAFECENTRE_PKG_v2)
                    || isSystemPkgInstalled(appContext, OPPO_GUARD_PKG_V2)
                    || isSystemPkgInstalled(appContext, OPPO_GUARD_PKG_V1)
                    || isSystemPkgInstalled(appContext, OPPO_SAFE_PKG)
                    || checkForVendor("Oppo")
        }

    //Funtouch OS (Vivo)
    val isVivo: Boolean
        get() {
            val IGOO_SECURE_PKG = "com.iqoo.secure"
            val VIVO_PERMMANAGER_PKG = "com.vivo.permissionmanager"
            val VIVO_PERMMANAGER_PKG_V2 = "com.vivo.abe"
            return (isSystemPkgInstalled(appContext, IGOO_SECURE_PKG)
                    || isSystemPkgInstalled(appContext, VIVO_PERMMANAGER_PKG)
                    || isSystemPkgInstalled(appContext, VIVO_PERMMANAGER_PKG_V2))
                    || checkForVendor("Vivo")
        }

    //Samsung OneUI/Samsung Experience/TouchWiz
    val isSamsung: Boolean
        get() {
            val SAMSUNG_SYSTEMMANAGER_ACTION = "com.samsung.android.sm.ACTION_BATTERY"
            // ANDROID 7.0/8.0
            val SAMSUNG_SYSTEMMANAGER_PACKAGE_V3 = "com.samsung.android.lool"
            // ANDROID 6.0/9.0
            val SAMSUNG_SYSTEMMANAGER_PACKAGE_V2 = "com.samsung.android.sm_cn"
            // ANDROID 5.0/5.1
            val SAMSUNG_SYSTEMMANAGER_PACKAGE_V1 = "com.samsung.android.sm"
            if ((isIntentAvailable(
                    appContext,
                    createIntent().setAction(SAMSUNG_SYSTEMMANAGER_ACTION)
                )
                        || isSystemPkgInstalled(appContext, SAMSUNG_SYSTEMMANAGER_PACKAGE_V3)
                        || isSystemPkgInstalled(appContext, SAMSUNG_SYSTEMMANAGER_PACKAGE_V2)
                        || isSystemPkgInstalled(appContext, SAMSUNG_SYSTEMMANAGER_PACKAGE_V1))
            )
                return true


            try {
                VERSION::class.java.getDeclaredField("SEM_PLATFORM_INT").getInt(null)
                return true
            } catch (ignore: Throwable) {
            }

            val packageManager = appContext.packageManager
            return packageManager.hasSystemFeature("com.samsung.feature.samsung_experience_mobile") ||
                    packageManager.hasSystemFeature("com.samsung.feature.samsung_experience_mobile_lite")
        }

    //Emui OS (Huawei/Honor)
    val isHuawei: Boolean
        get() = get(appContext, "ro.build.version.emui")?.isNotEmpty() == true

    //Miui OS (Xiaomi/Poco)
    val isMiui: Boolean
        get() = get(appContext, "ro.miui.ui.version.code")?.isNotEmpty() == true
}