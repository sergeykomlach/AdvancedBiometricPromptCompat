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

package dev.skomlach.common.misc.multiwindow

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

class MultiWindowSupport {
    companion object {
        private val realScreenSize = LruCache<Configuration, Point>(1)
        @SuppressLint("StaticFieldLeak")
        val ctx = AndroidContext.appContext
        fun isTablet(): Boolean {
            val resources = ctx.resources
            val configuration = AndroidContext.configuration ?: resources.configuration
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

    private val activity: Activity
        get() {
            return AndroidContext.activity ?: throw IllegalStateException("No activity on screen")
        }

    //Unlike Android N method, this one support also non-Nougat+ multiwindow modes (like Samsung/LG/Huawei/etc solutions)
    private fun checkIsInMultiWindow(): Boolean {
        val rect = Rect()
        val decorView = activity.findViewById<ViewGroup>(Window.ID_ANDROID_CONTENT)
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
        sb.append(activity.javaClass.simpleName + " Activity screen:")
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
        val decorView = activity.findViewById<ViewGroup>(Window.ID_ANDROID_CONTENT)
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
        sb.append(activity.javaClass.simpleName + " Activity screen:")
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

                val isMultiWindow = !isActivityFinished(activity) && activity.isInMultiWindowMode
                val sb = StringBuilder()
                sb.append(activity.javaClass.simpleName + " Activity screen:")
                log("isMultiWindow $isMultiWindow", sb)
                LogCat.logError(sb.toString())
                return isMultiWindow
            } else {
                //http://open-wiki.flyme.cn/index.php?title=%E5%88%86%E5%B1%8F%E9%80%82%E9%85%8D%E6%96%87%E6%A1%A3
                try {
                    val clazz = Class.forName("meizu.splitmode.FlymeSplitModeManager")
                    val b = clazz.getMethod("getInstance", Context::class.java)
                    val instance = b.invoke(null, activity)
                    val m = clazz.getMethod("isSplitMode")
                    val isMultiWindow = m.invoke(instance) as Boolean
                    val sb = StringBuilder()
                    sb.append(activity.javaClass.simpleName + " Activity screen:")
                    log("isMultiWindow $isMultiWindow", sb)
                    LogCat.logError(sb.toString())
                    return isMultiWindow
                } catch (ignore: Throwable) {
                }
                return if (!isActivityFinished(activity)) {
                    //general way - for OEM devices (Samsung, LG, Huawei) and/or in case API24 not fired for some reasons
                    checkIsInMultiWindow()
                } else {
                    false
                }
            }
        }
    private val navigationBarHeight: Int
        get() {
            if (!hasNavBar()) {
                return 0
            }
            val resources = activity.resources
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
    private val navigationBarWidth: Int
        get() {
            if (!hasNavBar()) {
                return 0
            }
            val resources = activity.resources
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

    private fun hasNavBar(): Boolean {
        val realSize = realScreenSize
        val realHeight = realSize.y
        val realWidth = realSize.x
        val bounds = WindowHelper.getCurrentWindowMetrics(activity)
        val displayHeight = bounds.height()
        val displayWidth = bounds.width()
        if (realWidth - displayWidth > 0 || realHeight - displayHeight > 0) {
            return true
        }
        val hasMenuKey = ViewConfiguration.get(activity).hasPermanentMenuKey()
        val hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK)
        val hasHomeKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_HOME)
        val hasNoCapacitiveKeys = !hasMenuKey && !hasBackKey && !hasHomeKey
        val resources = activity.resources
        val id = resources.getIdentifier("config_showNavigationBar", "bool", "android")
        val hasOnScreenNavBar = id > 0 && resources.getBoolean(id)
        return hasOnScreenNavBar || hasNoCapacitiveKeys
    }

    // status bar height
    private val statusBarHeight: Int
        get() {
            // status bar height
            var statusBarHeight = 0
            val resourceId =
                activity.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                statusBarHeight = activity.resources.getDimensionPixelSize(resourceId)
            }
            return statusBarHeight
        }//This should be close, as lower API devices should not have window navigation bars//this may not be 100% accurate, but it's all we've got//reflection for this weird in-between time

    //new pleasant way to get real metrics
    private val realScreenSize: Point
        get() {
            val configuration = activity.resources.configuration
            val point = Companion.realScreenSize[configuration]
            return if (point != null) {
                point
            } else {
                val bounds = WindowHelper.getMaximumWindowMetrics(activity)
                val realWidth = bounds.width()
                val realHeight = bounds.height()
                val size = Point(realWidth, realHeight)
                Companion.realScreenSize.put(configuration, size)
                size
            }
        }
    val screenOrientation: Int
        get() {
            var orientation = activity.resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_UNDEFINED) {
                val bounds = WindowHelper.getCurrentWindowMetrics(activity)
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