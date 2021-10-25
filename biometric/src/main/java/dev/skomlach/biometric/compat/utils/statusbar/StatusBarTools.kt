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

import android.graphics.Color
import android.os.Build
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.Window
import androidx.annotation.ColorInt
import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

@RestrictTo(RestrictTo.Scope.LIBRARY)
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
            view.post(runnable)
        } else {
            view.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (HelperTool.isVisible(view, 100)) {
                        if (view.viewTreeObserver.isAlive) {
                            view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            view.post(runnable)
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
            val isDark = ColorUtil.trueDarkColor(color)

            //emulate navbar color via translucent and custom views
            //On Android6+ and some OEM device we can enable DarkIcons
            if (!StatusBarIconsDarkMode.setDarkIconMode(
                    window,
                    !isDark,
                    BarType.NAVBAR
                )
            ) { //in other cases - make color a bit 'darker'
                if (!isDark) {
                    color = ColorUtil.blend(color, Color.BLACK, alpha.toDouble())
                }
            }
            if (Build.VERSION.SDK_INT >= 21) {
                window.navigationBarColor = color
            }
            //add divider for android 9
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = dividerColor
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced =
                    ColorUtil.colorDistance(color, Color.TRANSPARENT) <= 0.1
            }
        } catch (e: Throwable) {
            e(e)
        }
    }

    private fun setStatusBarColor(window: Window, @ColorInt c: Int) {
        var color = c
        try {
            if (TURNOFF_TINT) return
            if (translucentStatusBar) color = Color.TRANSPARENT
            val isDark = ColorUtil.trueDarkColor(color)

            //emulate statusbar color via translucent and custom views
            //On Android6+ and some OEM device we can enable DarkIcons
            if (!StatusBarIconsDarkMode.setDarkIconMode(
                    window,
                    !isDark,
                    BarType.STATUSBAR
                )
            ) { //in other cases - make color a bit 'darker'
                if (!isDark) {
                    color = ColorUtil.blend(color, Color.BLACK, alpha.toDouble())
                }
            }
            if (Build.VERSION.SDK_INT >= 21) {
                window.statusBarColor = color
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced =
                    ColorUtil.colorDistance(color, Color.TRANSPARENT) <= 0.1
            }
        } catch (e: Throwable) {
            e(e)
        }
    }
}