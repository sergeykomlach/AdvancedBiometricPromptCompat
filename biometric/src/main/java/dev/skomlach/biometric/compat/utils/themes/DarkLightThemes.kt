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

package dev.skomlach.biometric.compat.utils.themes

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.utils.SettingsHelper

@RestrictTo(RestrictTo.Scope.LIBRARY)
object DarkLightThemes {
    @JvmStatic
    fun isNightMode(context: Context): Boolean {
        return UiModeManager.MODE_NIGHT_YES == getNightMode(context)
    }

    @JvmStatic
    fun getNightMode(context: Context): Int {
        return when (getIsOsDarkTheme(context)) {
            DarkThemeCheckResult.DARK -> {
                UiModeManager.MODE_NIGHT_YES
            }
            DarkThemeCheckResult.LIGHT -> {
                UiModeManager.MODE_NIGHT_NO
            }
            else -> {
                if (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                ) {
                    return UiModeManager.MODE_NIGHT_YES
                }
                if (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                    return UiModeManager.MODE_NIGHT_YES
                }
                val modeFromSettings =
                    SettingsHelper.getInt(context, "ui_night_mode", UiModeManager.MODE_NIGHT_NO)
                if (modeFromSettings != UiModeManager.MODE_NIGHT_NO) {
                    return modeFromSettings
                }
                val mUiModeManager =
                    context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
                mUiModeManager?.currentModeType ?: UiModeManager.MODE_NIGHT_NO
            }
        }
    }
}