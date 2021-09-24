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

package dev.skomlach.biometric.compat.utils.statusbar

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RestrictTo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.skomlach.common.misc.Utils
import java.util.*

@RestrictTo(RestrictTo.Scope.LIBRARY)
object StatusBarIconsDarkMode {
    private var SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = 0x00002000
    private var SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = 0x00000010

    init {
        SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = try {
            //Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
             View::class.java.getField("SYSTEM_UI_FLAG_LIGHT_STATUS_BAR").getInt(null)
        } catch (e: Exception) {
            0x00002000
        }
        SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = try {
            //Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
           View::class.java.getField("SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR").getInt(null)
        } catch (e: Exception) {
            0x00000010
        }
    }
    private fun setMiuiIconDarkMode(window: Window, lightBars: Boolean, type: BarType): Boolean {
        try {
            //constants for MIUI similar to "SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR" stored in separate class
            val layoutParams = Class.forName("android.view.MiuiWindowManager\$LayoutParams")
            val allFields = layoutParams.fields
            for (field in allFields) {
                val name = field.name
                if (!name.contains("_DARK_")) continue
                if (type == BarType.STATUSBAR && !name.lowercase(Locale.ROOT).contains("status")) {
                    continue
                }
                if (type == BarType.NAVBAR && !name.lowercase(Locale.ROOT).contains("nav")) {
                    continue
                }
                val darkModeFlag =
                    field.getInt(null) //because its static fields - access without object
                return HelperTool.setMIUIFlag(window, lightBars, darkModeFlag)
            }
        } catch (e: Throwable) {
        }
        return false
    }

    private fun setFlymeIconDarkMode(window: Window, lightBars: Boolean, type: BarType): Boolean {
        try {
            //FlymeOS expand WindowManager.LayoutParams class and add some private fields
            val allFields = WindowManager.LayoutParams::class.java.declaredFields
            for (field in allFields) {
                val name = field.name
                if (!name.contains("_DARK_")) continue
                if (type == BarType.STATUSBAR && !name.lowercase(Locale.ROOT).contains("status")) {
                    continue
                }
                if (type == BarType.NAVBAR && !name.lowercase(Locale.ROOT).contains("nav")) {
                    continue
                }
                field.isAccessible = true
                val bits = field.getInt(null)
                return HelperTool.setFlameFlag(window, lightBars, bits)
            }
        } catch (e: Throwable) {
        }
        return false
    }

    fun setDarkIconMode(window: Window, lightBars: Boolean, type: BarType): Boolean {
        //TODO: Don't works properly with navbar

//        if (Utils.isAtLeastR) {
//           WindowCompat.getInsetsController(window, window.decorView)?.let { windowInsetsController ->
//                if (type == BarType.STATUSBAR)
//                    windowInsetsController.isAppearanceLightStatusBars = lightBars
//                else
//                    windowInsetsController.isAppearanceLightNavigationBars = lightBars
//                return true
//            }
//        }

        //Android6+ should deal with DarkIcons without problems
        val bits =
            if (type == BarType.STATUSBAR) SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bits != -1 && HelperTool.setUIVisibilityFlag(
                window,
                lightBars,
                bits
            )
        ) return true

        //in other case - try to use OEM solutions
        return if (setFlymeIconDarkMode(window, lightBars, type)) {
            true
        } else setMiuiIconDarkMode(window, lightBars, type)
    }

}