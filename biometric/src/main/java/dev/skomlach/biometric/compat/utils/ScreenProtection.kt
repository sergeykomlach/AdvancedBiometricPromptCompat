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
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.core.view.ViewCompat
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.protection.A11yDetection
import java.util.WeakHashMap

object ScreenProtection {
    private data class ProtectedViewState(
        val importantForAccessibility: Int,
        val importantForAutofill: Int?,
        val originalAccessibilityDelegate: View.AccessibilityDelegate?,
        val accessibilityDataSensitive: Int?
    )

    // Main-thread state. The process observer never owns a View or Activity.
    private val protectedViews = WeakHashMap<View, Unit>()
    private var readerCheck = 0L
    private val observeSettings by lazy {
        val context = AndroidContext.appContext
        val resolver = context.contentResolver
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                onServicesChanged(context)
            }
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, observer
            )
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(AccessibilityManager::class.java)
                    ?.addAccessibilityServicesStateChangeListener(ExecutorHelper.executor) {
                        onServicesChanged(context)
                    }
            }
        }.onFailure {
            runCatching { resolver.unregisterContentObserver(observer) }
            BiometricLoggerImpl.e("ScreenProtection", "Unable to observe accessibility settings")
        }.isSuccess
    }

    private fun onServicesChanged(context: Context) {
        A11yDetection.clearCache()
        ExecutorHelper.post {
            protectedViews.keys.forEach(::restrictAccessibility)
            refreshReaderAccess(context)
        }
    }

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
        // FLAG_SECURE is the mandatory protection. Keep it outside the optional
        // Android-version-specific controls so their failures cannot hide or
        // accidentally bypass the primary security decision.
        if (disableWindow) {
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } catch (error: Throwable) {
                BiometricLoggerImpl.e("ScreenProtection", "addFlags(FLAG_SECURE) failed", error)
                try {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } catch (fallbackError: Throwable) {
                    BiometricLoggerImpl.e(
                        "ScreenProtection",
                        "SECURITY FAILURE: setFlags(FLAG_SECURE) fallback failed",
                        fallbackError
                    )
                    throw fallbackError
                }
            }
        } else {
            try {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } catch (error: Throwable) {
                BiometricLoggerImpl.e("ScreenProtection", "clearFlags(FLAG_SECURE) failed", error)
                try {
                    window.setFlags(
                        0,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } catch (fallbackError: Throwable) {
                    BiometricLoggerImpl.e(
                        "ScreenProtection",
                        "setFlags(clear FLAG_SECURE) fallback failed",
                        fallbackError
                    )
                    throw fallbackError
                }
            }
        }

        if (includeHostActivity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                (window.context as? Activity)?.setRecentsScreenshotEnabled(!disableWindow)
            }.onFailure { error ->
                BiometricLoggerImpl.e("ScreenProtection", error)
            }
        }

        val canHideOverlays = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching {
            PermissionUtils.INSTANCE.hasSelfPermissions(Manifest.permission.HIDE_OVERLAY_WINDOWS)
        }.getOrDefault(false)
        if (canHideOverlays) {
            runCatching {
                window.setHideOverlayWindows(disableWindow)
            }.onFailure { error ->
                BiometricLoggerImpl.e("ScreenProtection", error)
            }
        }
    }

    fun applyProtectionInWindow(
        window: Window?,
        disableWindow: Boolean = true,
        includeHostActivity: Boolean = false
    ) {
        if (window == null) return

        applyProtectionInWindowInternal(window, disableWindow, includeHostActivity)
        applyProtectionInView(
            window.findViewById(Window.ID_ANDROID_CONTENT) ?: return,
            disableWindow
        )
    }

    fun applyProtectionInView(
        view: View,
        disableView: Boolean = true
    ) {
        try {
            if (disableView) {
                protectView(view)
                protectedViews[view] = Unit
                refreshReaderAccess(view.context.applicationContext)
            } else {
                protectedViews.remove(view)
                rollbackView(view)
            }
        } catch (e: Exception) {
            //not sure is exception can happen, but better to track at least
            BiometricLoggerImpl.e("ScreenProtection", e)
        }
    }

    private fun refreshReaderAccess(context: Context, legacyRetries: Int = 2) {
        val check = ++readerCheck
        val generation = A11yDetection.settingsGeneration
        if (protectedViews.isEmpty()) return
        ExecutorHelper.startOnBackground {
            if (!observeSettings) return@startOnBackground
            val allowed = A11yDetection.hasWhiteListedService(context)
            val retry = !allowed && legacyRetries > 0 && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                !Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).isNullOrEmpty()
            ExecutorHelper.post {
                if (check != readerCheck) return@post
                if (generation != A11yDetection.settingsGeneration) {
                    protectedViews.keys.forEach(::restrictAccessibility)
                    refreshReaderAccess(context, legacyRetries)
                    return@post
                }
                if (allowed) {
                    protectedViews.keys.forEach { view ->
                        val state = view.getTag(R.id.bio_tag_screen_protection_state) as? ProtectedViewState
                            ?: return@forEach
                        ViewCompat.setImportantForAccessibility(view, state.importantForAccessibility)
                        view.accessibilityDelegate = state.originalAccessibilityDelegate
                    }
                } else {
                    protectedViews.keys.forEach(::restrictAccessibility)
                    if (retry) {
                        ExecutorHelper.postDelayed({
                            if (check == readerCheck) {
                                refreshReaderAccess(context, legacyRetries - 1)
                            }
                        }, LEGACY_SERVICE_BIND_RETRY_MS)
                    }
                }
            }
        }
    }

    private const val LEGACY_SERVICE_BIND_RETRY_MS = 750L

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

        restrictAccessibility(view)

        invokeAccessibilityDataSensitiveApiOrNull(supportsAccessibilityDataSensitiveApi()) {
            view.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
        }

        view.setTag(R.id.bio_tag_screen_protection_applied, true)
    }

    private fun restrictAccessibility(view: View) {
        ViewCompat.setImportantForAccessibility(view, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        view.accessibilityDelegate = blockingDelegate
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

        state.accessibilityDataSensitive?.let {
            invokeAccessibilityDataSensitiveApiOrNull(supportsAccessibilityDataSensitiveApi()) {
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
            originalAccessibilityDelegate = AccessibilityDelegateAccess.get(view),
            accessibilityDataSensitive = invokeAccessibilityDataSensitiveApiOrNull(
                supportsAccessibilityDataSensitiveApi()
            ) {
                if (view.isAccessibilityDataSensitive) View.ACCESSIBILITY_DATA_SENSITIVE_YES else View.ACCESSIBILITY_DATA_SENSITIVE_NO
            }
        )

        view.setTag(R.id.bio_tag_screen_protection_state, state)
    }
}

private fun supportsAccessibilityDataSensitiveApi(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

internal fun <T> invokeAccessibilityDataSensitiveApiOrNull(
    isSupported: Boolean,
    apiCall: () -> T
): T? {
    if (!isSupported) return null

    return try {
        apiCall()
    } catch (_: NoSuchMethodError) {
        null
    }
}
