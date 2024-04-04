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

package dev.skomlach.common.multiwindow

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.*
import androidx.collection.LruCache
import androidx.window.WindowHelper
import dev.skomlach.common.R
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.isActivityFinished

class MultiWindowSupport private constructor() {
    companion object {
        private val realScreenSize = LruCache<Configuration, Point>(1)

        @SuppressLint("StaticFieldLeak")
        private val instance = MultiWindowSupport()
        fun get(): MultiWindowSupport {
            return instance
        }

        fun isTablet(): Boolean {
            val ctx = AndroidContext.activity ?: AndroidContext.appContext
            val resources = ctx.resources
            val configuration = AndroidContext.appConfiguration ?: resources.configuration
            val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ctx.createConfigurationContext(configuration).resources
            } else {
                @Suppress("DEPRECATION")
                resources.updateConfiguration(configuration, resources.displayMetrics)
                resources
            }
            return res.getBoolean(R.bool.biometric_compat_is_tablet)
        }
    }

    //Unlike Android N method, this one support also non-Nougat+ multiwindow modes (like Samsung/LG/Huawei/etc solutions)
    private fun checkIsInMultiWindow(): Boolean {
        val rect = Rect()
        val decorView = AndroidContext.activity?.findViewById<ViewGroup>(Window.ID_ANDROID_CONTENT)?:return false
        decorView.getGlobalVisibleRect(rect)
        if (rect.width() == 0 && rect.height() == 0) {
            return false
        }
        val realScreenSize = realScreenSize
        val statusBarHeight = statusBarHeight
        val navigationBarHeight = navigationBarHeight
        val navigationBarWidth = navigationBarWidth
        var h = realScreenSize.y - rect.height() - statusBarHeight - navigationBarHeight
        var w = realScreenSize.x - rect.width()
        val isSmartphone = !isTablet()
        if (isSmartphone && screenOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            h += navigationBarHeight
            w -= navigationBarWidth
        }
        val isMultiWindow = h != 0 || w != 0
        val locationOnScreen = IntArray(2)
        decorView.getLocationOnScreen(locationOnScreen)

        val sb = StringBuilder()
        sb.append(AndroidContext.activity?.javaClass?.simpleName + " Activity screen:")
        log("isMultiWindow $isMultiWindow", sb)
        log("final " + w + "x" + h + "", sb)
        log("NavBarW/H " + navigationBarWidth + "x" + navigationBarHeight, sb)
        log("statusBarH $statusBarHeight", sb)
        log("View $rect", sb)
        log("realScreenSize $realScreenSize", sb)

        LogCat.logError(sb.toString())
        return isMultiWindow
    }

    private fun log(msg: Any, sb: java.lang.StringBuilder) {
        sb.append(" [").append(msg).append("] ")
    }

    fun isWindowOnScreenBottom(): Boolean {
        val rect = Rect()
        val decorView = AndroidContext.activity?.findViewById<ViewGroup>(Window.ID_ANDROID_CONTENT)?:return false
        decorView.getGlobalVisibleRect(rect)
        if (rect.width() == 0 && rect.height() == 0) {
            return false
        }
        val realScreenSize = realScreenSize
        val locationOnScreen = IntArray(2)
        decorView.getLocationOnScreen(locationOnScreen)
        val isWindowOnScreenBottom =
            isInMultiWindow && (realScreenSize.y / 2 < locationOnScreen[1] + (rect.width() / 2))
        val sb = StringBuilder()
        sb.append(AndroidContext.activity?.javaClass?.simpleName + " Activity screen:")
        log("isWindowOnScreenBottom $isWindowOnScreenBottom", sb)
        LogCat.logError(sb.toString())
        return isWindowOnScreenBottom
    }

    //Should work on API24+ and support almost all devices types, include Chromebooks and foldable devices
    //http://open-wiki.flyme.cn/index.php?title=%E5%88%86%E5%B1%8F%E9%80%82%E9%85%8D%E6%96%87%E6%A1%A3
    //general way - for OEM devices (Samsung, LG, Huawei) and/or in case API24 not fired for some reasons
    val isInMultiWindow: Boolean
        get() {
            //Should work on API24+ and support almost all devices types, include Chromebooks and foldable devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

                val isMultiWindow = AndroidContext.activity?.isInMultiWindowMode == true
                val sb = StringBuilder()
                sb.append(AndroidContext.activity?.javaClass?.simpleName + " Activity screen:")
                log("isMultiWindow $isMultiWindow", sb)
                LogCat.logError(sb.toString())
                return isMultiWindow
            } else {
                //http://open-wiki.flyme.cn/index.php?title=%E5%88%86%E5%B1%8F%E9%80%82%E9%85%8D%E6%96%87%E6%A1%A3
                try {
                    val clazz = Class.forName("meizu.splitmode.FlymeSplitModeManager")
                    val b = clazz.getMethod("getInstance", Context::class.java)
                    val instance = b.invoke(null, AndroidContext.activity?:return false)
                    val m = clazz.getMethod("isSplitMode")
                    val isMultiWindow = m.invoke(instance) as Boolean
                    val sb = StringBuilder()
                    sb.append(AndroidContext.activity?.javaClass?.simpleName + " Activity screen:")
                    log("isMultiWindow $isMultiWindow", sb)
                    LogCat.logError(sb.toString())
                    return isMultiWindow
                } catch (ignore: Throwable) {
                }
                return if (AndroidContext.activity!=null) {
                    //general way - for OEM devices (Samsung, LG, Huawei) and/or in case API24 not fired for some reasons
                    checkIsInMultiWindow()
                } else {
                    false
                }
            }
        }
    val navigationBarHeight: Int
        get() {
            if (!hasNavBar()) {
                return 0
            }
            val resources = (AndroidContext.activity?:AndroidContext.appContext).resources
            val orientation = screenOrientation
            val isSmartphone = !isTablet()
            val resourceId: Int = if (!isSmartphone) {
                resources.getIdentifier(
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) "navigation_bar_height" else "navigation_bar_height_landscape",
                    "dimen",
                    "android"
                )
            } else {
                resources.getIdentifier(
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) "navigation_bar_height" else "navigation_bar_width",
                    "dimen",
                    "android"
                )
            }
            return if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else 0
        }
    val navigationBarWidth: Int
        get() {
            if (!hasNavBar()) {
                return 0
            }
            val resources = (AndroidContext.activity?:AndroidContext.appContext).resources
            val orientation = screenOrientation
            val isSmartphone = !isTablet()
            val resourceId: Int = if (!isSmartphone) {
                resources.getIdentifier(
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) "navigation_bar_height_landscape" else "navigation_bar_height",
                    "dimen",
                    "android"
                )
            } else {
                resources.getIdentifier(
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) "navigation_bar_width" else "navigation_bar_height",
                    "dimen",
                    "android"
                )
            }
            return if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else 0
        }

    fun hasNavBar(): Boolean {
        val realSize = realScreenSize
        val realHeight = realSize.y
        val realWidth = realSize.x
        val bounds = WindowHelper.getCurrentWindowMetrics(AndroidContext.activity?:return true)
        val displayHeight = bounds.height()
        val displayWidth = bounds.width()
        if (realWidth - displayWidth > 0 || realHeight - displayHeight > 0) {
            return true
        }
        val hasMenuKey = ViewConfiguration.get(AndroidContext.activity?:return true).hasPermanentMenuKey()
        val hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK)
        val hasHomeKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_HOME)
        val hasNoCapacitiveKeys = !hasMenuKey && !hasBackKey && !hasHomeKey
        val resources = (AndroidContext.activity?:AndroidContext.appContext).resources
        val id = resources.getIdentifier("config_showNavigationBar", "bool", "android")
        val hasOnScreenNavBar = id > 0 && resources.getBoolean(id)
        return hasOnScreenNavBar || hasNoCapacitiveKeys
    }

    // status bar height
    val statusBarHeight: Int
        get() {
            // status bar height
            var statusBarHeight = 0
            val resourceId =
                (AndroidContext.activity?:AndroidContext.appContext).resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                statusBarHeight = (AndroidContext.activity?:AndroidContext.appContext).resources.getDimensionPixelSize(resourceId)
            }
            return statusBarHeight
        }//This should be close, as lower API devices should not have window navigation bars//this may not be 100% accurate, but it's all we've got//reflection for this weird in-between time

    //new pleasant way to get real metrics
    val realScreenSize: Point
        get() {
            val configuration = (AndroidContext.activity?:AndroidContext.appContext).resources.configuration
            val point = Companion.realScreenSize[configuration]
            return if (point != null) {
                point
            } else {
                val bounds = WindowHelper.getMaximumWindowMetrics(AndroidContext.activity?:return Point())
                val realWidth = bounds.width()
                val realHeight = bounds.height()
                val size = Point(realWidth, realHeight)
                Companion.realScreenSize.put(configuration, size)
                size
            }
        }
    val screenOrientation: Int
        get() {
            var orientation = (AndroidContext.activity?:AndroidContext.appContext).resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_UNDEFINED) {
                val bounds = WindowHelper.getCurrentWindowMetrics(AndroidContext.activity?:return Configuration.ORIENTATION_SQUARE)
                orientation = if (bounds.width() == bounds.height()) {
                    Configuration.ORIENTATION_SQUARE
                } else {
                    if (bounds.width() < bounds.height()) {
                        Configuration.ORIENTATION_PORTRAIT
                    } else {
                        Configuration.ORIENTATION_LANDSCAPE
                    }
                }
            }
            return orientation
        }


}