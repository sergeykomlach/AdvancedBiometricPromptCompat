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

package dev.skomlach.common.statusbar

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.Window
import androidx.annotation.ColorInt
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.SettingsHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.themes.DarkThemeCheckResult
import dev.skomlach.common.themes.getIsOsDarkTheme
import java.time.LocalTime


object StatusBarTools {
    /* //FIXME: Display cutout support
     * https://github.com/QMUI/QMUI_Android/tree/2689199dda27a6c9163fe54faa05e2d3a8447416/qmui/src/main/java/com/qmuiteam/qmui/util
     * https://open.oppomobile.com/wiki/doc#id=10159
     * https://mini.eastday.com/bdmip/180411011257629.html
     * https://com-it.tech/archives/55704
     * https://blog.csdn.net/sinat_29874521/article/details/80224447
     * https://developer.huawei.com/consumer/en/devservice/doc/30210
     *
     * http://thoughtnerds.com/2018/03/10-things-you-should-know-about-android-p/
     * */
    private const val TURNOFF_TINT = false
    private const val translucentNavBar = false
    private const val translucentStatusBar = false
    private const val alpha = 0.65f

    //setSystemUiVisibility has effect only if View is visible
    fun setNavBarAndStatusBarColors(
        window: Window,
        @ColorInt colorNavBar: Int,
        @ColorInt dividerColor: Int,
        @ColorInt colorStatusBar: Int
    ) {
        val runnable = Runnable {
            setStatusBarColor(window, colorStatusBar)
            setNavBarColor(window, colorNavBar, dividerColor)
        }
        val view = window.decorView
        if (HelperTool.isVisible(view, 100)) {
            ExecutorHelper.post(runnable)
        } else {
            view.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (HelperTool.isVisible(view, 100)) {
                        if (view.viewTreeObserver.isAlive) {
                            view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            ExecutorHelper.post(runnable)
                        }
                    }
                }
            })
        }
    }

    private fun setNavBarColor(window: Window, @ColorInt c: Int, @ColorInt dividerColor: Int) {
        var color = c
        try {
            if (TURNOFF_TINT) return
            if (translucentNavBar) color = Color.TRANSPARENT
            val isDark =
                if (ColorUtil.colorDistance(
                        color,
                        Color.TRANSPARENT
                    ) <= 0.1
                ) isNightMode(window.context) else ColorUtil.isDark(
                    color
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            //emulate navbar color via translucent and custom views
            //On Android6+ and some OEM device we can enable DarkIcons
            if (!StatusBarIconsDarkMode.setDarkIconMode(
                    window,
                    !isDark,
                    BarType.NAVBAR
                )
            ) { //in other cases - make color a bit 'darker'
                color = if (!isDark) {
                    ColorUtil.blend(color, Color.BLACK, alpha.toDouble())
                } else
                    ColorUtil.blend(color, Color.WHITE, alpha.toDouble())
            }
            if (Build.VERSION.SDK_INT >= 21) {
                window.navigationBarColor = color
            }
            //add divider for android 9
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = dividerColor
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

    private fun setStatusBarColor(window: Window, @ColorInt c: Int) {
        var color = c
        try {
            if (TURNOFF_TINT) return
            if (translucentStatusBar) color = Color.TRANSPARENT
            val isDark =
                if (ColorUtil.colorDistance(
                        color,
                        Color.TRANSPARENT
                    ) <= 0.1
                ) isNightMode(window.context) else ColorUtil.isDark(
                    color
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
            }
            //emulate statusbar color via translucent and custom views
            //On Android6+ and some OEM device we can enable DarkIcons
            if (!StatusBarIconsDarkMode.setDarkIconMode(
                    window,
                    !isDark,
                    BarType.STATUSBAR
                )
            ) { //in other cases - make color a bit 'darker'
                color = if (!isDark) {
                    ColorUtil.blend(color, Color.BLACK, alpha.toDouble())
                } else
                    ColorUtil.blend(color, Color.WHITE, alpha.toDouble())
            }
            if (Build.VERSION.SDK_INT >= 21) {
                window.statusBarColor = color
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

    private fun isNightMode(context: Context): Boolean {
        return UiModeManager.MODE_NIGHT_YES == getNightMode(
            context
        )
    }

    private fun getNightMode(context: Context): Int {
        return when (getIsOsDarkTheme(context)) {
            DarkThemeCheckResult.DARK -> {
                UiModeManager.MODE_NIGHT_YES
            }

            DarkThemeCheckResult.LIGHT -> {
                UiModeManager.MODE_NIGHT_NO
            }

            else -> {
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