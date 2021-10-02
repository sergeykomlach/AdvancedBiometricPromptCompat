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

package dev.skomlach.common.contextprovider

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.hardware.display.DisplayManagerCompat
import dev.skomlach.common.misc.Utils.isAtLeastR


fun Context.getFixedContext(): Context {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        getWindowContext(getDisplayContext(this))
    } else getWindowContext(this)
}

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
private fun getDisplayContext(context: Context): Context {
    //check if context already has display
    try {
        if (isAtLeastR && context.display != null)
            return context
    } catch (ignore: Exception) {
    }

    try {
        val dm = DisplayManagerCompat.getInstance(context)
        dm.getDisplay(Display.DEFAULT_DISPLAY)?.let { d ->
        val ctx = context.createDisplayContext(d)//LOL - if you use AccessibilityService - warning anyway happens here :)
        return ctx ?: context
        }
    } catch (ignore: Exception) {
    }
    //give up, lets use at least original Context

    return context
}

private fun getWindowContext(context: Context): Context {
    if (isAtLeastR) {
        try {
            return context.createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                null
            )//for now - fail always for 3rd party apps
        } catch (ignore: Exception) {
        }
        //give up, lets use at least original Context
    }
    return context
}