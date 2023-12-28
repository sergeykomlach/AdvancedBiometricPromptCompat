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

package dev.skomlach.common.protection

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import dev.skomlach.common.logging.LogCat

object A11yDetection {
    //isAccessibilityTool
    fun shouldTrustA11y(cnt: Context): Boolean {
        try {
            val accessibilityEnabled =
                Settings.Secure.getInt(cnt.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
            val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')

            if (accessibilityEnabled == 1) {
                val settingValue = Settings.Secure.getString(
                    cnt.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                mStringColonSplitter.setString(settingValue)
                val list = mutableListOf<ComponentName?>()
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    list.add(ComponentName.unflattenFromString(accessibilityService))
                }
                LogCat.log(
                    "A11yDetection",
                    list
                )

                return list.filterNotNull().filter {
                    !InstallerID.verifyInstallerId(cnt, it.packageName)
                }.also {
                    LogCat.logError(
                        "A11yDetection",
                        it
                    )
                }.isEmpty()
            }
        } catch (e: Throwable) {
            LogCat.logError(
                "A11yDetection",
                e.message, e
            )
        }
        return false
    }
}