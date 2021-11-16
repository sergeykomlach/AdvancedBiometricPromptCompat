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

package androidx.window

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.DisplayCutout
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import java.lang.reflect.InvocationTargetException

object WindowHelper {
    //NOTE!! Date: 16/11/2021
    //Keep this code up-to-date manually
    //Latest codebase
    //implementation "androidx.window:window:1.0.0-beta03"


    /* For some reasons exception happens on foldable devices if we use androidx.window.WindowManager(activity)

   Fatal Exception: java.lang.AbstractMethodError: abstract method "void androidx.window.sidecar.SidecarInterface$SidecarCallback.onDeviceStateChanged(androidx.window.sidecar.SidecarDeviceState)"
      at androidx.window.sidecar.SamsungSidecarImpl.updateDevicePosture(SamsungSidecarImpl.java:86)
      at androidx.window.sidecar.SamsungSidecarImpl.access$000(SamsungSidecarImpl.java:43)
      at androidx.window.sidecar.SamsungSidecarImpl$SamsungSidecarCallbackListener.onDeviceStateChanged(SamsungSidecarImpl.java:62)
      at android.view.WindowManagerGlobal.lambda$handleDeviceStateChangedEventIfNeedLocked$2$WindowManagerGlobal(WindowManagerGlobal.java:1150)
      at android.view.-$$Lambda$WindowManagerGlobal$jfB49vh4VxV3HJn7Ersg7WbshIM.run(-.java:2)
      at android.os.Handler.handleCallback(Handler.java:938)
      at android.os.Handler.dispatchMessage(Handler.java:99)
      at android.os.Looper.loop(Looper.java:246)
      at android.app.ActivityThread.main(ActivityThread.java:8506)
      at java.lang.reflect.Method.invoke(Method.java)
      at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:602)
      at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1130)

      And

      Fatal Exception: java.lang.AbstractMethodError: abstract method "void androidx.window.sidecar.SidecarInterface$SidecarCallback.onDeviceStateChanged(androidx.window.sidecar.SidecarDeviceState)"
      at androidx.window.sidecar.MicrosoftSurfaceSidecar.updateDeviceState(MicrosoftSurfaceSidecar.java:159)
      at androidx.window.sidecar.MicrosoftSurfaceSidecar$1.deviceStateChanged(MicrosoftSurfaceSidecar.java:192)
      at android.vendor.screenlayout.service.IWindowExtensionCallbackInterface$Stub.onTransact(IWindowExtensionCallbackInterface.java:94)
      at android.os.Binder.execTransactInternal(Binder.java:1021)
      at android.os.Binder.execTransact(Binder.java:994)
   * */
    fun getMaximumWindowMetrics(mActivity: Activity): Rect {
//        return RectCalculator.getOrCreate().computeMaximumRect(mActivity).bounds
        return RectCalculatorCompat.computeMaximumRect(mActivity)
    }

    fun getCurrentWindowMetrics(mActivity: Activity): Rect {
//        return RectCalculator.getOrCreate().computeCurrentRect(mActivity).bounds
        return RectCalculatorCompat.computeCurrentRect(mActivity)

    }


    internal object RectCalculatorCompat {
        /**
         * Computes the current [Rect] for a given [Activity]
         * @see RectCalculator.computeCurrentRect
         */
        fun computeCurrentRect(activity: Activity): Rect {
            val bounds = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    ActivityCompatHelperApi30.currentWindowBounds(activity)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    computeWindowBoundsQ(activity)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    computeWindowBoundsP(activity)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                    computeWindowBoundsN(activity)
                }
                else -> {
                    computeWindowBoundsIceCreamSandwich(activity)
                }
            }
            return Rect(bounds)
        }

        /**
         * Computes the maximum [Rect] for a given [Activity]
         * @see RectCalculator.computeMaximumRect
         */
        fun computeMaximumRect(activity: Activity): Rect {
            val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ActivityCompatHelperApi30.maximumWindowBounds(activity)
            } else {
                // [WindowManager#getDefaultDisplay] is deprecated but we have this for
                // compatibility with older versions
                @Suppress("DEPRECATION")
                val display = activity.windowManager.defaultDisplay
                val displaySize = getRealSizeForDisplay(display)
                Rect(0, 0, displaySize.x, displaySize.y)
            }
            return Rect(bounds)
        }

        /** Computes the window bounds for [Build.VERSION_CODES.Q].  */
        @SuppressLint("BanUncheckedReflection", "BlockedPrivateApi")
        @RequiresApi(Build.VERSION_CODES.Q)
        internal fun computeWindowBoundsQ(activity: Activity): Rect {
            var bounds: Rect
            val config = activity.resources.configuration
            try {
                val windowConfigField =
                    Configuration::class.java.getDeclaredField("windowConfiguration")
                windowConfigField.isAccessible = true
                val windowConfig = windowConfigField[config]
                val getBoundsMethod = windowConfig.javaClass.getDeclaredMethod("getBounds")
                bounds = Rect(getBoundsMethod.invoke(windowConfig) as Rect)
            } catch (e: NoSuchFieldException) {

                // If reflection fails for some reason default to the P implementation which still
                // has the ability to account for display cutouts.
                bounds = computeWindowBoundsP(activity)
            } catch (e: NoSuchMethodException) {

                bounds = computeWindowBoundsP(activity)
            } catch (e: IllegalAccessException) {

                bounds = computeWindowBoundsP(activity)
            } catch (e: InvocationTargetException) {

                bounds = computeWindowBoundsP(activity)
            }
            return bounds
        }

        /**
         * Computes the window bounds for [Build.VERSION_CODES.P].
         *
         *
         * NOTE: This method may result in incorrect values if the [android.content.res.Resources]
         * value stored at 'navigation_bar_height' does not match the true navigation bar inset on
         * the window.
         *
         */
        @SuppressLint("BanUncheckedReflection", "BlockedPrivateApi")
        @RequiresApi(Build.VERSION_CODES.P)
        internal fun computeWindowBoundsP(activity: Activity): Rect {
            val bounds = Rect()
            val config = activity.resources.configuration
            try {
                val windowConfigField =
                    Configuration::class.java.getDeclaredField("windowConfiguration")
                windowConfigField.isAccessible = true
                val windowConfig = windowConfigField[config]

                // In multi-window mode we'll use the WindowConfiguration#mBounds property which
                // should match the window size. Otherwise we'll use the mAppBounds property and
                // will adjust it below.
                if (ActivityCompatHelperApi24.isInMultiWindowMode(activity)) {
                    val getAppBounds = windowConfig.javaClass.getDeclaredMethod("getBounds")
                    bounds.set((getAppBounds.invoke(windowConfig) as Rect))
                } else {
                    val getAppBounds = windowConfig.javaClass.getDeclaredMethod("getAppBounds")
                    bounds.set((getAppBounds.invoke(windowConfig) as Rect))
                }
            } catch (e: NoSuchFieldException) {

                getRectSizeFromDisplay(activity, bounds)
            } catch (e: NoSuchMethodException) {

                getRectSizeFromDisplay(activity, bounds)
            } catch (e: IllegalAccessException) {

                getRectSizeFromDisplay(activity, bounds)
            } catch (e: InvocationTargetException) {

                getRectSizeFromDisplay(activity, bounds)
            }
            val platformWindowManager = activity.windowManager

            // [WindowManager#getDefaultDisplay] is deprecated but we have this for
            // compatibility with older versions
            @Suppress("DEPRECATION")
            val currentDisplay = platformWindowManager.defaultDisplay
            val realDisplaySize = Point()
            // [Display#getRealSize] is deprecated but we have this for
            // compatibility with older versions
            @Suppress("DEPRECATION")
            (DisplayCompatHelperApi17.getRealSize(
                display = currentDisplay,
                point = realDisplaySize
            ))
            if (!ActivityCompatHelperApi24.isInMultiWindowMode(activity)) {
                // The activity is not in multi-window mode. Check if the addition of the
                // navigation bar size to mAppBounds results in the real display size and if so
                // assume the nav bar height should be added to the result.
                val navigationBarHeight = getNavigationBarHeight(activity)
                if (bounds.bottom + navigationBarHeight == realDisplaySize.y) {
                    bounds.bottom += navigationBarHeight
                } else if (bounds.right + navigationBarHeight == realDisplaySize.x) {
                    bounds.right += navigationBarHeight
                } else if (bounds.left == navigationBarHeight) {
                    bounds.left = 0
                }
            }
            if ((bounds.width() < realDisplaySize.x || bounds.height() < realDisplaySize.y) &&
                !ActivityCompatHelperApi24.isInMultiWindowMode(activity)
            ) {
                // If the corrected bounds are not the same as the display size and the activity is
                // not in multi-window mode it is possible there are unreported cutouts inset-ing
                // the window depending on the layoutInCutoutMode. Check for them here by getting
                // the cutout from the display itself.
                val displayCutout = getCutoutForDisplay(currentDisplay)
                if (displayCutout != null) {
                    if (bounds.left == DisplayCompatHelperApi28.safeInsetLeft(displayCutout)) {
                        bounds.left = 0
                    }
                    if (realDisplaySize.x - bounds.right == DisplayCompatHelperApi28.safeInsetRight(
                            displayCutout
                        )
                    ) {
                        bounds.right += DisplayCompatHelperApi28.safeInsetRight(displayCutout)
                    }
                    if (bounds.top == DisplayCompatHelperApi28.safeInsetTop(displayCutout)) {
                        bounds.top = 0
                    }
                    if (realDisplaySize.y - bounds.bottom == DisplayCompatHelperApi28.safeInsetBottom(
                            displayCutout
                        )
                    ) {
                        bounds.bottom += DisplayCompatHelperApi28.safeInsetBottom(displayCutout)
                    }
                }
            }
            return bounds
        }

        private fun getRectSizeFromDisplay(activity: Activity, bounds: Rect) {
            // [WindowManager#getDefaultDisplay] is deprecated but we have this for
            // compatibility with older versions
            @Suppress("DEPRECATION")
            val defaultDisplay = activity.windowManager.defaultDisplay
            // [Display#getRectSize] is deprecated but we have this for
            // compatibility with older versions
            @Suppress("DEPRECATION")
            defaultDisplay.getRectSize(bounds)
        }

        /**
         * Computes the window bounds for platforms between [Build.VERSION_CODES.N]
         * and [Build.VERSION_CODES.O_MR1], inclusive.
         *
         *
         * NOTE: This method may result in incorrect values under the following conditions:
         *
         *  * If the activity is in multi-window mode the origin of the returned bounds will
         * always be anchored at (0, 0).
         *  * If the [android.content.res.Resources] value stored at 'navigation_bar_height' does
         *  not match the true navigation bar size the returned bounds will not take into account
         *  the navigation
         * bar.
         *
         */
        @RequiresApi(Build.VERSION_CODES.N)
        internal fun computeWindowBoundsN(activity: Activity): Rect {
            val bounds = Rect()
            // [WindowManager#getDefaultDisplay] is deprecated but we have this for
            // compatibility with older versions
            @Suppress("DEPRECATION")
            val defaultDisplay = activity.windowManager.defaultDisplay
            // [Display#getRectSize] is deprecated but we have this for
            // compatibility with older versions
            @Suppress("DEPRECATION")
            defaultDisplay.getRectSize(bounds)
            if (!ActivityCompatHelperApi24.isInMultiWindowMode(activity)) {
                // The activity is not in multi-window mode. Check if the addition of the
                // navigation bar size to Display#getSize() results in the real display size and
                // if so return this value. If not, return the result of Display#getSize().
                val realDisplaySize = getRealSizeForDisplay(defaultDisplay)
                val navigationBarHeight = getNavigationBarHeight(activity)
                if (bounds.bottom + navigationBarHeight == realDisplaySize.y) {
                    bounds.bottom += navigationBarHeight
                } else if (bounds.right + navigationBarHeight == realDisplaySize.x) {
                    bounds.right += navigationBarHeight
                }
            }
            return bounds
        }

        /**
         * Computes the window bounds for platforms between [Build.VERSION_CODES.JELLY_BEAN]
         * and [Build.VERSION_CODES.M], inclusive.
         *
         *
         * Given that multi-window mode isn't supported before N we simply return the real display
         * size which should match the window size of a full-screen app.
         */
        @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        internal fun computeWindowBoundsIceCreamSandwich(activity: Activity): Rect {
            // [WindowManager#getDefaultDisplay] is deprecated but we have this for
            // compatibility with older versions
            @Suppress("DEPRECATION")
            val defaultDisplay = activity.windowManager.defaultDisplay
            val realDisplaySize = getRealSizeForDisplay(defaultDisplay)
            val bounds = Rect()
            if (realDisplaySize.x == 0 || realDisplaySize.y == 0) {
                // [Display#getRectSize] is deprecated but we have this for
                // compatibility with older versions
                @Suppress("DEPRECATION")
                defaultDisplay.getRectSize(bounds)
            } else {
                bounds.right = realDisplaySize.x
                bounds.bottom = realDisplaySize.y
            }
            return bounds
        }

        /**
         * Returns the full (real) size of the display, in pixels, without subtracting any window
         * decor or applying any compatibility scale factors.
         *
         *
         * The size is adjusted based on the current rotation of the display.
         *
         * @return a point representing the real display size in pixels.
         *
         * @see Display.getRealSize
         */
        @VisibleForTesting
        @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        internal fun getRealSizeForDisplay(display: Display): Point {
            val size = Point()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                DisplayCompatHelperApi17.getRealSize(display, size)
            } else {
                try {
                    val getRealSizeMethod = Display::class.java.getDeclaredMethod(
                        "getRealSize",
                        Point::class.java
                    )
                    getRealSizeMethod.isAccessible = true
                    getRealSizeMethod.invoke(display, size)
                } catch (e: NoSuchMethodException) {

                } catch (e: IllegalAccessException) {

                } catch (e: InvocationTargetException) {

                }
            }
            return size
        }

        /**
         * Returns the [android.content.res.Resources] value stored as 'navigation_bar_height'.
         *
         *
         * Note: This is error-prone and is **not** the recommended way to determine the size
         * of the overlapping region between the navigation bar and a given window. The best
         * approach is to acquire the [android.view.WindowInsets].
         */
        private fun getNavigationBarHeight(context: Context): Int {
            val resources = context.resources
            val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            return if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else 0
        }

        /**
         * Returns the [DisplayCutout] for the given display. Note that display cutout returned
         * here is for the display and the insets provided are in the display coordinate system.
         *
         * @return the display cutout for the given display.
         */
        @SuppressLint("BanUncheckedReflection")
        @RequiresApi(Build.VERSION_CODES.P)
        private fun getCutoutForDisplay(display: Display): DisplayCutout? {
            var displayCutout: DisplayCutout? = null
            try {
                val displayInfoClass = Class.forName("android.view.DisplayInfo")
                val displayInfoConstructor = displayInfoClass.getConstructor()
                displayInfoConstructor.isAccessible = true
                val displayInfo = displayInfoConstructor.newInstance()
                val getDisplayInfoMethod = display.javaClass.getDeclaredMethod(
                    "getDisplayInfo", displayInfo.javaClass
                )
                getDisplayInfoMethod.isAccessible = true
                getDisplayInfoMethod.invoke(display, displayInfo)
                val displayCutoutField = displayInfo.javaClass.getDeclaredField("displayCutout")
                displayCutoutField.isAccessible = true
                val cutout = displayCutoutField[displayInfo]
                if (cutout is DisplayCutout) {
                    displayCutout = cutout
                }
            } catch (e: ClassNotFoundException) {

            } catch (e: NoSuchMethodException) {

            } catch (e: NoSuchFieldException) {

            } catch (e: IllegalAccessException) {

            } catch (e: InvocationTargetException) {

            } catch (e: InstantiationException) {

            }
            return displayCutout
        }

        @RequiresApi(Build.VERSION_CODES.N)
        internal object ActivityCompatHelperApi24 {
            fun isInMultiWindowMode(activity: Activity): Boolean {
                return activity.isInMultiWindowMode
            }
        }

        @RequiresApi(Build.VERSION_CODES.R)
        internal object ActivityCompatHelperApi30 {
            fun currentWindowBounds(activity: Activity): Rect {
                return activity.windowManager.currentWindowMetrics.bounds
            }

            fun maximumWindowBounds(activity: Activity): Rect {
                return activity.windowManager.maximumWindowMetrics.bounds
            }
        }

        @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        internal object DisplayCompatHelperApi17 {

            @Suppress("DEPRECATION")
            fun getRealSize(display: Display, point: Point) {
                display.getRealSize(point)
            }
        }

        @RequiresApi(Build.VERSION_CODES.P)
        internal object DisplayCompatHelperApi28 {

            fun safeInsetLeft(displayCutout: DisplayCutout): Int {
                return displayCutout.safeInsetLeft
            }

            fun safeInsetTop(displayCutout: DisplayCutout): Int {
                return displayCutout.safeInsetTop
            }

            fun safeInsetRight(displayCutout: DisplayCutout): Int {
                return displayCutout.safeInsetRight
            }

            fun safeInsetBottom(displayCutout: DisplayCutout): Int {
                return displayCutout.safeInsetBottom
            }
        }
    }
}