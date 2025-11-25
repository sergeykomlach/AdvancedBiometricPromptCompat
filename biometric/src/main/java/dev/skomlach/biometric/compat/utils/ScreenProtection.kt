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

package dev.skomlach.biometric.compat.utils

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.core.os.BuildCompat
import androidx.core.view.ViewCompat
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.protection.A11yDetection

object ScreenProtection {
    //disable next features:

    //Screenshots
    //Accessibility Services
    //Android Oreo autofill in the app

    private fun applyProtectionInWindowInternal(
        window: Window?,
        disableWindow: Boolean = true,
        includeHostActivity: Boolean = false
    ) {
        try {
            if (window == null) return
            if (disableWindow) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                window.context?.let {
                    if (it is Activity)
                        if (includeHostActivity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            it.setRecentsScreenshotEnabled(false)
                        }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && PermissionUtils.INSTANCE.hasSelfPermissions(
                        Manifest.permission.HIDE_OVERLAY_WINDOWS
                    )
                )
                    try {
                        window.setHideOverlayWindows(true)
                    } catch (se: SecurityException) {
                    }
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                window.context?.let {
                    if (it is Activity)
                        if (includeHostActivity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            it.setRecentsScreenshotEnabled(true)
                        }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && PermissionUtils.INSTANCE.hasSelfPermissions(
                        Manifest.permission.HIDE_OVERLAY_WINDOWS
                    )
                )
                    try {
                        window.setHideOverlayWindows(false)
                    } catch (se: SecurityException) {

                    }
            }
        } catch (e: Exception) {

        }
    }

    fun applyProtectionInWindow(
        window: Window?,
        disableWindow: Boolean = true,
        includeHostActivity: Boolean = false
    ) {
        try {
            applyProtectionInWindowInternal(
                window,
                disableWindow,
                includeHostActivity
            )
            applyProtectionInView(
                window?.findViewById(
                    Window.ID_ANDROID_CONTENT
                ) ?: return
            )
        } catch (_: Exception) {

        }
    }

    fun applyProtectionInView(
        view: View
    ) {
        try {
            if (A11yDetection.hasWhiteListedService(view.context))
                return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ViewCompat.setImportantForAutofill(
                    view,
                    View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                )
            }


            ViewCompat.setImportantForAccessibility(
                view,
                ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            )
            if (BuildCompat.isAtLeastU()) {
                view.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
            }
            //Note: View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS doesn't have affect
            //for 3rd party password managers
            view.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun sendAccessibilityEvent(host: View, eventType: Int) {
                }

                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?
                ): Boolean {
                    return false
                }

                override fun sendAccessibilityEventUnchecked(
                    host: View,
                    event: AccessibilityEvent
                ) {
                }

                override fun dispatchPopulateAccessibilityEvent(
                    host: View,
                    event: AccessibilityEvent
                ): Boolean {
                    return false
                }

                override fun onPopulateAccessibilityEvent(
                    host: View,
                    event: AccessibilityEvent
                ) {
                }

                override fun onInitializeAccessibilityEvent(
                    host: View,
                    event: AccessibilityEvent
                ) {
                }

                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo
                ) {
                }

                override fun addExtraDataToAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo, extraDataKey: String,
                    arguments: Bundle?
                ) {
                }

                override fun onRequestSendAccessibilityEvent(
                    host: ViewGroup, child: View,
                    event: AccessibilityEvent
                ): Boolean {
                    return false
                }

                override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProvider? {
                    return null
                }
            }
        } catch (e: Exception) {
            //not sure is exception can happens, but better to track at least
            BiometricLoggerImpl.e(e, e.message)
        }
    }

}