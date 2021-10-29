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

import android.view.ViewGroup
import android.view.Window
import dev.skomlach.biometric.compat.BiometricPromptCompat

class ActivityViewWatcher(
    private val compatBuilder: BiometricPromptCompat.Builder,
    private val forceToCloseCallback: ForceToCloseCallback
) {
    private val views = ActiveWindow.getActiveWindows(compatBuilder.getContext()).toMutableList()
    private val activeView = ActiveWindow.getActiveWindow(views)
    private val windowForegroundBlurring =
        WindowForegroundBlurring(
            compatBuilder,
            activeView.findViewById(Window.ID_ANDROID_CONTENT) as ViewGroup,
            object : ForceToCloseCallback {
                override fun onCloseBiometric() {
                    for (back in backgroundBlurs) {
                        back.resetListeners()
                    }
                    forceToCloseCallback.onCloseBiometric()
                }
            })
    private val backgroundBlurs = mutableListOf<WindowBackgroundBlurring>()

    init {
        views.remove(activeView)
        for (view in views) {
            backgroundBlurs.add(WindowBackgroundBlurring(view.findViewById(Window.ID_ANDROID_CONTENT) as ViewGroup))
        }

    }

    fun setupListeners() {
        for (back in backgroundBlurs) {
            back.setupListeners()
        }
        windowForegroundBlurring.setupListeners()

    }

    fun resetListeners() {
        for (back in backgroundBlurs) {
            back.resetListeners()
        }
        windowForegroundBlurring.resetListeners()

    }

    interface ForceToCloseCallback {
        fun onCloseBiometric()
    }

}