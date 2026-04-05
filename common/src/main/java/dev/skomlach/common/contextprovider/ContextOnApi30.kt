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

package dev.skomlach.common.contextprovider

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import dev.skomlach.common.misc.SettingsHelper


fun Context.animationsDisabled(): Boolean =
    //Developers mode enabled
    SettingsHelper.getInt(
        this,
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
        0
    ) != 0 &&
            //Progress bar animation, etc
            (SettingsHelper.getFloat(
                this,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) <= 0.0f
                    //Fragment/Activity switching animation
                    || SettingsHelper.getFloat(
                this,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            ) <= 0.0f
                    //Dialogs appearing animation
                    || SettingsHelper.getFloat(
                this,
                Settings.Global.WINDOW_ANIMATION_SCALE,
                1.0f
            ) <= 0.0f)

fun Context.getFixedContext(
    type: Int = WindowManager.LayoutParams.TYPE_APPLICATION
): Context {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> getWindowContextApi31(type)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> getWindowContextApi30(type)
        else -> this
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Context.getWindowContextApi31(type: Int): Context {
    runCatching {
        val currentDisplay = try {
            display
        } catch (_: Exception) {
            getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        }
        if (currentDisplay != null) {
            return createWindowContext(currentDisplay, type, null)
        }
    }
    return this
}

@RequiresApi(Build.VERSION_CODES.R)
private fun Context.getWindowContextApi30(type: Int): Context {
    runCatching {
        return createWindowContext(type, null)
    }
    return this
}