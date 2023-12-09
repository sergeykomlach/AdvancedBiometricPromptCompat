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

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.WindowManager
import androidx.core.util.ObjectsCompat
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e


object ActiveWindow {
    private var clazz: Class<*>? = null
    private var windowManager: Any? = null
    private var windowManagerClazz: Class<*>? = null

    init {
        try {
            clazz = Class.forName("android.view.ViewRootImpl")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                windowManagerClazz = Class.forName("android.view.WindowManagerGlobal")
                windowManagerClazz?.getMethod("getInstance")?.invoke(null)
                    .also { windowManager = it }
            } else {
                windowManagerClazz = Class.forName("android.view.WindowManagerImpl")
                windowManagerClazz?.getMethod("getDefault")?.invoke(null)
                    .also { windowManager = it }
            }
        } catch (e: Throwable) {
            e(e)
        }
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

    fun getActiveWindows(activity: FragmentActivity?): List<View> {
        val screens = mutableListOf<View>()
        val list = viewRoots
        for (i in list.indices) {
            val viewParent = list[i]
            try {
                val view = clazz?.getMethod("getView")?.invoke(viewParent) as View
                val type = (view.layoutParams as WindowManager.LayoutParams).type
                if (type >= WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW) {
                    continue
                }
                if (activity == null || !viewBelongActivity(view, activity))
                    continue
                screens.add(view)
            } catch (e: Throwable) {
                e(e, "ActiveWindow.getActiveView")
            }
        }
        return screens
    }

    private fun viewBelongActivity(view: View?, activity: Activity): Boolean {
        if (view == null) return false
        var context: Context? = extractActivity(view.context)
        if (context == null) context = view.context
        if (ObjectsCompat.equals(activity, context)) {
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
        while (true) {
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
    }

    // Filter out inactive view roots
    private val viewRoots: List<ViewParent>
        get() {
            val viewRoots: MutableList<ViewParent> = ArrayList()
            try {
                val rootsField = windowManagerClazz?.getDeclaredField("mRoots")
                val isAccessibleRootsField = rootsField?.isAccessible
                try {
                    if (isAccessibleRootsField == false) rootsField.isAccessible = true
                    val stoppedField = clazz?.getDeclaredField("mStopped")
                    val isAccessible = stoppedField?.isAccessible
                    try {
                        if (isAccessible == false) stoppedField.isAccessible = true
                        val lst = rootsField?.get(windowManager)
                        val viewParents: MutableList<ViewParent> = ArrayList()
                        try {
                            viewParents.addAll((lst as List<ViewParent>))
                        } catch (ignore: ClassCastException) {
                            val parents = lst as Array<ViewParent>
                            viewParents.addAll(listOf(*parents))
                        }
                        // Filter out inactive view roots
                        for (viewParent in viewParents) {
                            val stopped = stoppedField?.get(viewParent) as Boolean
                            if (!stopped) {
                                viewRoots.add(viewParent)
                            }
                        }
                    } finally {
                        if (isAccessible == false) stoppedField.isAccessible = false
                    }
                } finally {
                    if (isAccessibleRootsField == false) rootsField.isAccessible = false
                }
            } catch (e: Exception) {
                e(e, "ActiveWindow")
            }
            return viewRoots
        }

}