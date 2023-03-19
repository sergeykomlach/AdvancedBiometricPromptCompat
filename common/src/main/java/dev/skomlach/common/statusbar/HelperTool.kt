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

import android.graphics.Rect
import android.view.View
import android.view.Window
import android.view.WindowManager


object HelperTool {
    /**
     * Whether the view is at least certain % visible
     */
    fun isVisible( //@Nullable final View rootView,
        view: View?, minPercentageViewed: Int
    ): Boolean {
        // ListView & GridView both call detachFromParent() for views that can be recycled for
        // new data. This is one of the rare instances where a view will have a null parent for
        // an extended period of time and will not be the main window.
        // view.getGlobalVisibleRect() doesn't check that case, so if the view has visibility
        // of View.VISIBLE but it's group has no parent it is likely in the recycle bin of a
        // ListView / GridView and not on screen.
        if (view == null || view.visibility != View.VISIBLE) {
            return false
        }
        val mClipRect = Rect()
        if (!view.getGlobalVisibleRect(mClipRect)) {
            // Not visible
            return false
        }

        // % visible check - the cast is to avoid int overflow for large views.
        val visibleViewArea = mClipRect.height().toLong() * mClipRect.width()
        val totalViewArea = view.height.toLong() * view.width
        return if (totalViewArea <= 0) {
            false
        } else 100 * visibleViewArea >= minPercentageViewed * totalViewArea
    }

    //Set UI visibility flags if not set yet
    fun setUIVisibilityFlag(window: Window, set: Boolean, bits: Int): Boolean {
        val view = window.decorView
        val oldVis = view.systemUiVisibility
        var newVis = oldVis
        newVis = if (set) {
            newVis or bits
        } else {
            newVis and bits.inv()
        }
        return if (newVis != oldVis) {
            view.systemUiVisibility = newVis
            true
        } else {
            //already set
            if (set && newVis and bits == bits) true else !set && newVis and bits != bits
        }
    }

    //Set Window flags (if not set yet) in "general" way
    fun setFlag(window: Window, set: Boolean, bits: Int): Boolean {
        val lp = window.attributes
        val oldVis = lp.flags
        var newVis = oldVis
        newVis = if (set) {
            newVis or bits
        } else {
            newVis and bits.inv()
        }
        return if (newVis != oldVis) {
            lp.flags = newVis
            window.attributes = lp
            true
        } else {
            //already set
            if (set && newVis and bits == bits) true else !set && newVis and bits != bits
        }
    }

    /*
     * Note:
     * Next code very similar to "general" BUT used BitFlags not compatible with "general" API and leads to unexpected bugs.
     * - For example flags from MIUI set window in non-touchable mode :)
     *
     * See also:
     * https://www.programcreek.com/java-api-examples/?code=Lingzh0ng/BrotherWeather/BrotherWeather-master/weather/src/main/java/com/wearapay/brotherweather/common/utils/StatusBarTextColorUtils.java
     * https://dev.mi.com/doc/p=4769/
     * */
    //Set Window flags (if not set yet) in Flyme-specific way
    fun setFlameFlag(window: Window, set: Boolean, bits: Int): Boolean {
        try {
            val lp = window.attributes
            val meizuFlags = WindowManager.LayoutParams::class.java.getDeclaredField("meizuFlags")
            meizuFlags.isAccessible = true
            val oldVis = meizuFlags.getInt(lp)
            var newVis = oldVis
            newVis = if (set) {
                newVis or bits
            } else {
                newVis and bits.inv()
            }
            if (newVis != oldVis) {
                meizuFlags.setInt(
                    lp,
                    newVis
                ) // field is a part of WindowManager.LayoutParams, so at first - change value for the field
                window.attributes = lp //and update window attributes
                return true
            } else {
                //already set
                if (set && newVis and bits == bits) return true
                if (!set && newVis and bits != bits) return true
            }
        } catch (ignore: Throwable) {
        }
        return false
    }

    //Set Window flags (if not set yet) in MIUI-specific way
    fun setMIUIFlag(window: Window, set: Boolean, bits: Int): Boolean {
        //I found that clearExtraFlags/addExtraFlags available at least for MIUI10, not sure about older
        try {
            val clazz: Class<out Window> = window.javaClass
            val extraFlagField1 = clazz.getMethod("clearExtraFlags", Int::class.javaPrimitiveType)
            extraFlagField1.invoke(window, bits)
            if (set) {
                val extraFlagField2 = clazz.getMethod("addExtraFlags", Int::class.javaPrimitiveType)
                extraFlagField2.invoke(window, bits)
            }
            return true
        } catch (ignore: Throwable) {
        }
        //try to use solution from https://dev.mi.com/doc/p=4769/
        try {
            val clazz: Class<out Window> = window.javaClass
            val extraFlagField = clazz.getMethod(
                "setExtraFlags",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            extraFlagField.invoke(window, if (set) bits else 0, bits)
            return true
        } catch (ignore: Throwable) {
        }
        return false
    }
}