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

package dev.skomlach.biometric.compat.utils.themes

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.SettingsHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.themes.DarkThemeCheckResult
import dev.skomlach.common.themes.getIsOsDarkTheme
import java.time.LocalTime


object DarkLightThemes {
    fun isNightModeCompatWithInscreen(context: Context): Boolean {
        return isNightMode(context, true)
    }

    fun isNightMode(context: Context, shouldInscreenCaseAffected: Boolean = false): Boolean {
        return UiModeManager.MODE_NIGHT_YES == getNightMode(context, shouldInscreenCaseAffected)
    }

    fun getNightModeCompatWithInscreen(context: Context): Int {
        return getNightMode(context, true)
    }

    fun getNightMode(context: Context, shouldInscreenCaseAffected: Boolean = false): Int {
        if (shouldInscreenCaseAffected &&
            DevicesWithKnownBugs.isOnePlus &&
            !Utils.isAtLeastT &&
            DevicesWithKnownBugs.hasUnderDisplayFingerprint
        )
            return UiModeManager.MODE_NIGHT_YES
        return when (getIsOsDarkTheme(context)) {
            DarkThemeCheckResult.DARK -> {
                UiModeManager.MODE_NIGHT_YES
            }

            DarkThemeCheckResult.LIGHT -> {
                UiModeManager.MODE_NIGHT_NO
            }

            else -> {
                AndroidContext.configuration?.let { config ->
                    if ((config.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) ||
                        (Utils.isAtLeastR && config.isNightModeActive)
                    )
                        return UiModeManager.MODE_NIGHT_YES
                }
                Resources.getSystem().configuration?.let { config ->
                    if ((config.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) ||
                        (Utils.isAtLeastR && config.isNightModeActive)
                    )
                        return UiModeManager.MODE_NIGHT_YES
                }

                val mUiModeManager =
                    context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
                val modeFromSettings =
                    SettingsHelper.getInt(context, "ui_night_mode", UiModeManager.MODE_NIGHT_NO)
                if (modeFromSettings != UiModeManager.MODE_NIGHT_NO) {
                    if (modeFromSettings == UiModeManager.MODE_NIGHT_YES)
                        return UiModeManager.MODE_NIGHT_YES
                    else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val start = mUiModeManager?.customNightModeStart
                            val end = mUiModeManager?.customNightModeEnd
                            val now = LocalTime.now()
                            if (now.isAfter(start) && now.isBefore(end))
                                return UiModeManager.MODE_NIGHT_YES
                        }
                    }
                } else {
                    val nightMode = mUiModeManager?.nightMode ?: UiModeManager.MODE_NIGHT_NO
                    if (nightMode != UiModeManager.MODE_NIGHT_NO) {
                        if (nightMode == UiModeManager.MODE_NIGHT_YES)
                            return UiModeManager.MODE_NIGHT_YES
                        else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val start = mUiModeManager?.customNightModeStart
                                val end = mUiModeManager?.customNightModeEnd
                                val now = LocalTime.now()
                                if ((now.equals(start) || now.equals(end)) || (now.isAfter(start) && now.isBefore(
                                        end
                                    ))
                                )
                                    return UiModeManager.MODE_NIGHT_YES
                            }
                        }
                    }
                }
                UiModeManager.MODE_NIGHT_NO
            }
        }
    }
}