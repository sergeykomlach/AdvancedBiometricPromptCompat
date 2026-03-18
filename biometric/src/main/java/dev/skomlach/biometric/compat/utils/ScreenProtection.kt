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
import dev.skomlach.biometric.compat.BuildConfig
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.protection.A11yDetection

object ScreenProtection {
    private data class ProtectedViewState(
        val importantForAccessibility: Int,
        val importantForAutofill: Int?,
        val originalAccessibilityDelegate: View.AccessibilityDelegate?,
        val accessibilityDataSensitive: Int?
    )

    private val blockingDelegate = object : View.AccessibilityDelegate() {
        override fun sendAccessibilityEvent(host: View, eventType: Int) = Unit

        override fun performAccessibilityAction(
            host: View,
            action: Int,
            args: Bundle?
        ): Boolean = false

        override fun sendAccessibilityEventUnchecked(
            host: View,
            event: AccessibilityEvent
        ) = Unit

        override fun dispatchPopulateAccessibilityEvent(
            host: View,
            event: AccessibilityEvent
        ): Boolean = false

        override fun onPopulateAccessibilityEvent(
            host: View,
            event: AccessibilityEvent
        ) = Unit

        override fun onInitializeAccessibilityEvent(
            host: View,
            event: AccessibilityEvent
        ) = Unit

        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfo
        ) = Unit

        override fun addExtraDataToAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfo,
            extraDataKey: String,
            arguments: Bundle?
        ) = Unit

        override fun onRequestSendAccessibilityEvent(
            host: ViewGroup,
            child: View,
            event: AccessibilityEvent
        ): Boolean = false

        override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProvider? = null
    }
    //disable next features:
    //Screenshots
    //Accessibility Services
    //Android Oreo autofill in the app

    private fun applyProtectionInWindowInternal(
        window: Window,
        disableWindow: Boolean = true,
        includeHostActivity: Boolean = false
    ) {
        try {
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
            if (window == null || BuildConfig.DEBUG) return
            applyProtectionInWindowInternal(
                window,
                disableWindow,
                includeHostActivity
            )
            applyProtectionInView(
                window?.findViewById(
                    Window.ID_ANDROID_CONTENT
                ) ?: return,
                disableWindow
            )
        } catch (_: Exception) {

        }
    }

    fun applyProtectionInView(
        view: View,
        disableView: Boolean = true
    ) {
        try {
            if (BuildConfig.DEBUG) return
            if (A11yDetection.hasWhiteListedService(view.context))
                return

            if (disableView) {
                protectView(view)
            } else {
                rollbackView(view)
            }
        } catch (e: Exception) {
            //not sure is exception can happen, but better to track at least
            BiometricLoggerImpl.e("ScreenProtection", e)
        }
    }

    private fun protectView(view: View) {
        val alreadyApplied =
            view.getTag(R.id.bio_tag_screen_protection_applied) as? Boolean ?: false

        if (!alreadyApplied) {
            saveOriginalState(view)
        }

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

        view.accessibilityDelegate = blockingDelegate
        view.setTag(R.id.bio_tag_screen_protection_applied, true)
    }

    private fun rollbackView(view: View) {
        val state =
            view.getTag(R.id.bio_tag_screen_protection_state) as? ProtectedViewState ?: run {
                view.setTag(R.id.bio_tag_screen_protection_applied, false)
                return
            }

        ViewCompat.setImportantForAccessibility(view, state.importantForAccessibility)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            state.importantForAutofill?.let {
                ViewCompat.setImportantForAutofill(view, it)
            }
        }

        if (BuildCompat.isAtLeastU()) {
            state.accessibilityDataSensitive?.let {
                view.setAccessibilityDataSensitive(it)
            }
        }

        view.accessibilityDelegate = state.originalAccessibilityDelegate

        view.setTag(R.id.bio_tag_screen_protection_applied, false)
        view.setTag(R.id.bio_tag_screen_protection_state, null)
    }

    private fun saveOriginalState(view: View) {
        val state = ProtectedViewState(
            importantForAccessibility = view.importantForAccessibility,
            importantForAutofill = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.importantForAutofill
            } else {
                null
            },
            originalAccessibilityDelegate = getCurrentAccessibilityDelegateCompat(view),
            accessibilityDataSensitive = if (BuildCompat.isAtLeastU()) {
                if (view.isAccessibilityDataSensitive) View.ACCESSIBILITY_DATA_SENSITIVE_YES else View.ACCESSIBILITY_DATA_SENSITIVE_NO
            } else {
                null
            }
        )

        view.setTag(R.id.bio_tag_screen_protection_state, state)
    }

    @Suppress("DiscouragedPrivateApi", "PrivateApi")
    private fun getCurrentAccessibilityDelegateCompat(view: View): View.AccessibilityDelegate? {
        return try {
            val field = View::class.java.getDeclaredField("mAccessibilityDelegate")
            field.isAccessible = true
            field.get(view) as? View.AccessibilityDelegate
        } catch (_: Throwable) {
            null
        }
    }
}