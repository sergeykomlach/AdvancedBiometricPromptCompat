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

package dev.skomlach.biometric.compat.utils.activityView

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import dev.skomlach.biometric.compat.utils.readPlatformOrLegacy
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.util.Collections
import java.util.IdentityHashMap


object ActiveWindow {
    @RequiresApi(Build.VERSION_CODES.Q)
    private object Api29 {
        @DoNotInline
        fun getViews(): List<View> = WindowInspector.getGlobalWindowViews()
    }

    fun getActiveWindow(list: List<View>): View? {
        var topView: View? = null
        for (i in list.indices) {
            val view = list[i]
            try {
                val type = (view.layoutParams as WindowManager.LayoutParams).type
                if (topView == null) {
                    topView = view
                } else {
                    val topViewType = (topView.layoutParams as WindowManager.LayoutParams).type
                    if (type > topViewType) {
                        topView = view
                    } else if (view.hasWindowFocus() && !topView.hasWindowFocus()) {
                        topView = view
                    }
                }
            } catch (e: Throwable) {
                e(e, "ActiveWindow.getActiveView")
            }
        }
        e("ActiveWindow.getActiveView-$topView")
        return topView
    }

    @SuppressLint("NewApi")
    fun getActiveWindows(activity: Activity?): List<View> {
        if (activity == null) return emptyList()
        val roots = readPlatformOrLegacy(
            platformAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            platformRead = { Api29.getViews() },
            legacyRead = { LegacyWindowRoots.getViews() },
            onLinkageError = { e(it, "ActiveWindow") }
        )
        val decor = activity.window.peekDecorView()
        val ownerToken = decor?.applicationWindowToken
        val seen = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
        // Include the directly owned window even if discovery missed it during attachment.
        return (roots + listOfNotNull(decor)).filter { view ->
            try {
                val type = (view.layoutParams as? WindowManager.LayoutParams)?.type
                seen.add(view) && view.isAttachedToWindow && view.windowVisibility == View.VISIBLE &&
                        type != null && type < WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW &&
                        ((ownerToken != null && ownerToken == view.applicationWindowToken) ||
                                viewBelongActivity(view, activity))
            } catch (error: Throwable) {
                e(error, "ActiveWindow.getActiveWindows")
                false
            }
        }
    }

    private fun viewBelongActivity(view: View?, activity: Activity): Boolean {
        if (view == null) return false
        var context: Context? = extractActivity(view.context)
        if (context == null) context = view.context
        if (activity === context) {
            return true
        } else if (view is ViewGroup) {
            val vg = view
            for (i in 0 until vg.childCount) {
                if (viewBelongActivity(vg.getChildAt(i), activity)) return true
            }
        }
        return false
    }

    private fun extractActivity(c: Context): Activity? {
        var context = c
        val seen = Collections.newSetFromMap(IdentityHashMap<Context, Boolean>())
        while (seen.add(context)) {
            context = when (context) {
                is Application -> {
                    return null
                }

                is Activity -> {
                    return context
                }

                is ContextWrapper -> {
                    val baseContext = context.baseContext
                    // Prevent Stack Overflow.
                    if (baseContext === context) {
                        return null
                    }
                    baseContext
                }

                else -> {
                    return null
                }
            }
        }
        return null
    }

}
