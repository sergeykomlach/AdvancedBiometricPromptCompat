/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.compat.impl.dialogs

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.os.BuildCompat
import androidx.core.view.ViewCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.WindowFocusChangedListener
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.biometric.compat.utils.monet.SystemColorScheme
import dev.skomlach.biometric.compat.utils.monet.toArgb
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes.getNightMode
import dev.skomlach.common.misc.Utils

internal class BiometricPromptCompatDialog(
    compatBuilder: BiometricPromptCompat.Builder,
    isInscreenLayout: Boolean
) : AppCompatDialog(
    ContextThemeWrapper(compatBuilder.getContext(), R.style.Theme_BiometricPromptDialog),
    R.style.Theme_BiometricPromptDialog
) {
    private val crossfader: TransitionDrawable

    @LayoutRes
    private val res: Int =
        if (isInscreenLayout) R.layout.biometric_prompt_dialog_content_inscreen else R.layout.biometric_prompt_dialog_content
    var title: TextView? = null
        private set
    var subtitle: TextView? = null
        private set
    var description: TextView? = null
        private set
    var status: TextView? = null
        private set
    var negativeButton: Button? = null
        private set
    var fingerprintIcon: FingerprintIconView? = null
        private set
    var authPreview: View? = null
        private set
    var rootView: View? = null
        private set
    private var focusListener: WindowFocusChangedListener? = null

    private val wallpaperChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateMonetColorsInternal()
        }
    }

    init {
        val NIGHT_MODE: Int
        val currentMode = getNightMode(context)
        NIGHT_MODE = if (currentMode == UiModeManager.MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else if (currentMode == UiModeManager.MODE_NIGHT_AUTO) {
            if (BuildCompat.isAtLeastP()) {
                //Android 9+ deal with dark mode natively
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            } else {
                AppCompatDelegate.MODE_NIGHT_AUTO_TIME
            }
        } else {
            if (BuildCompat.isAtLeastP()) {
                //Android 9+ deal with dark mode natively
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) delegate.localNightMode =
            NIGHT_MODE
        crossfader = TransitionDrawable(
            arrayOf<Drawable>(
                ColorDrawable(Color.TRANSPARENT),
                ColorDrawable(ContextCompat.getColor(context, R.color.window_bg))
            )
        )
        crossfader.isCrossFadeEnabled = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        e("WindowFocusChangedListenerDialog.hasFocus(1) - $hasFocus")
        if (focusListener != null) {
            val root = findViewById<View>(Window.ID_ANDROID_CONTENT)
            if (root != null) {
                if (ViewCompat.isAttachedToWindow(root)) {
                    focusListener?.hasFocus(root.hasWindowFocus())
                }
            }
        }
        updateMonetColorsInternal()
    }

    fun makeVisible() {
        (rootView?.parent as View?)?.alpha = 1f
    }

    fun makeInvisible() {
        (rootView?.parent as View?)?.alpha = 0.01f
    }

    override fun dismiss() {
        if (isShowing) {
            val animation = AnimationUtils.loadAnimation(context, R.anim.move_out)
            (rootView?.parent as View).background = crossfader
            crossfader.reverseTransition(animation.duration.toInt())
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    if (isShowing) {
                        super@BiometricPromptCompatDialog.dismiss()
                    }
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
            rootView?.startAnimation(animation)
        }
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(res)
        rootView = findViewById(R.id.dialogContent)
        rootView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                try {
                    @Suppress("DEPRECATION")
                    context.registerReceiver(
                        wallpaperChangedReceiver,
                        IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
                    )
                    updateMonetColorsInternal()
                } catch (e: Throwable) {
                    BiometricLoggerImpl.e(e, "setupMonet")
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                if (isShowing)
                    super@BiometricPromptCompatDialog.dismiss()
                try {
                    context.unregisterReceiver(wallpaperChangedReceiver)
                } catch (e: Throwable) {
                }
            }
        })
        title = rootView?.findViewById(R.id.title)
        subtitle = rootView?.findViewById(R.id.subtitle)
        description = rootView?.findViewById(R.id.description)
        status = rootView?.findViewById(R.id.status)
        negativeButton = rootView?.findViewById(android.R.id.button1)
        fingerprintIcon = rootView?.findViewById(R.id.fingerprint_icon)
        authPreview = rootView?.findViewById(R.id.auth_preview)

        (rootView?.parent as View).setOnClickListener { cancel() }
        rootView?.setOnClickListener(null)
        val animation = AnimationUtils.loadAnimation(context, R.anim.move_in)
        (rootView?.parent as View).background = crossfader
        crossfader.startTransition(animation.duration.toInt())
        rootView?.startAnimation(animation)
        ScreenProtection().applyProtectionInWindow(window ?: return)
    }
    private fun updateMonetColorsInternal() {
        if (Utils.isAtLeastS) {
            val negativeButtonColor = ContextCompat.getColor(
                context,
                if (Utils.isAtLeastS) R.color.material_blue_500 else R.color.material_deep_teal_500
            )

            val textColor = ContextCompat.getColor(context, R.color.textColor)

            try {
                val monetColors = SystemColorScheme(context)
                if (DarkLightThemes.isNightMode(context)) {
                    fingerprintIcon?.tintColor(monetColors.accent1[300]?.toArgb())
                    negativeButton?.setTextColor(
                        monetColors.accent2[100]?.toArgb() ?: negativeButtonColor
                    )
                    rootView?.findViewById<ViewGroup>(R.id.dialogLayout)?.let {
                        setTextToTextViews(it, monetColors.neutral1[50]?.toArgb() ?: textColor)
                        monetColors.neutral1[900]?.toArgb()?.let { color ->
                            ViewCompat.setBackgroundTintList(
                                it,
                                ColorStateList.valueOf(color)
                            )
                        } ?: run {
                            ViewCompat.setBackgroundTintList(
                                it, null
                            )
                        }

                    }
                } else {
                    fingerprintIcon?.tintColor(monetColors.accent1[600]?.toArgb())
                    negativeButton?.setTextColor(
                        monetColors.neutral2[500]?.toArgb() ?: negativeButtonColor
                    )
                    rootView?.findViewById<ViewGroup>(R.id.dialogLayout)?.let {
                        setTextToTextViews(it, monetColors.neutral1[900]?.toArgb() ?: textColor)
                        monetColors.neutral1[50]?.toArgb()?.let { color ->
                            ViewCompat.setBackgroundTintList(
                                it,
                                ColorStateList.valueOf(color)
                            )
                        } ?: run {
                            ViewCompat.setBackgroundTintList(
                                it, null
                            )
                        }
                    }

                }

            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e, "Monet colors")
            }
        }
    }

    private fun setTextToTextViews(view: View?, color: Int) {
        if (view is TextView && view !is Button) {
            view.setTextColor(color)
        } else if (view is ViewGroup) {
            val count = view.childCount
            for (i in 0 until count) {
                setTextToTextViews(view.getChildAt(i), color)
            }
        }
    }

    fun setWindowFocusChangedListener(listener: WindowFocusChangedListener?) {
        focusListener = listener
    }

    private inner class ScreenProtection {
        //disable next features:

        //Screenshots
        //Accessibility Services
        //Android Oreo autofill in the app

        fun applyProtectionInWindow(window: Window?) {
            try {
                applyProtectionInView(window?.findViewById(Window.ID_ANDROID_CONTENT) ?: return)
            } catch (e: Exception) {
                //not sure is exception can happens, but better to track at least
                BiometricLoggerImpl.e(e, "ActivityContextProvider")
            }
        }

        fun applyProtectionInView(view: View) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    ViewCompat.getImportantForAutofill(view) != View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                ) {
                    ViewCompat.setImportantForAutofill(
                        view,
                        View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    )
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
                        event: AccessibilityEvent?
                    ) {
                    }

                    override fun dispatchPopulateAccessibilityEvent(
                        host: View,
                        event: AccessibilityEvent?
                    ): Boolean {
                        return false
                    }

                    override fun onPopulateAccessibilityEvent(
                        host: View,
                        event: AccessibilityEvent?
                    ) {
                    }

                    override fun onInitializeAccessibilityEvent(
                        host: View,
                        event: AccessibilityEvent?
                    ) {
                    }

                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfo?
                    ) {
                    }

                    override fun addExtraDataToAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfo, extraDataKey: String,
                        arguments: Bundle?
                    ) {
                    }

                    override fun onRequestSendAccessibilityEvent(
                        host: ViewGroup, child: View?,
                        event: AccessibilityEvent?
                    ): Boolean {
                        return false
                    }

                    override fun getAccessibilityNodeProvider(host: View?): AccessibilityNodeProvider? {
                        return null
                    }
                }
            } catch (e: Exception) {
                //not sure is exception can happens, but better to track at least
                BiometricLoggerImpl.e(e, e.message)
            }
        }

    }
}