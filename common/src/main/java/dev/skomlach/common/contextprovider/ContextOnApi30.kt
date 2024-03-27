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
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.hardware.display.DisplayManagerCompat
import dev.skomlach.common.misc.Utils.isAtLeastR

fun Context.animationsEnabled(): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) true else
        !(Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        ) == 0f
                && Settings.Global.getFloat(
            contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1.0f
        ) == 0f
                && Settings.Global.getFloat(
            contentResolver,
            Settings.Global.WINDOW_ANIMATION_SCALE,
            1.0f
        ) == 0f)

fun Context.getFixedContext(type: Int = WindowManager.LayoutParams.TYPE_APPLICATION): Context {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        getDisplayContext(this, type)
    } else getWindowContext(this, type)
}

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
private fun getDisplayContext(context: Context, type: Int): Context {
    //check if context already has display
    try {
        if (isAtLeastR) {
            context.display?.let { d ->
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.createWindowContext(d, type, null)
                } else context.createDisplayContext(d).createWindowContext(type, null)
            }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    }
    try {
        val dm = DisplayManagerCompat.getInstance(context)
        dm.getDisplay(Display.DEFAULT_DISPLAY)?.let { d ->
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.createWindowContext(d, type, null)
            } else context.createDisplayContext(d).createWindowContext(type, null)
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    }
    //give up, lets use at least original Context
    return getWindowContext(context, type)
}

private fun getWindowContext(context: Context, type: Int): Context {
    if (isAtLeastR) {
        try {
            return context.createWindowContext(type, null)//for now - fail always for 3rd party apps
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        //give up, lets use at least original Context
    }
    return context
}