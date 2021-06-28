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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
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
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialog
import dev.skomlach.biometric.compat.impl.dialogs.FingerprintIconView
import dev.skomlach.biometric.compat.utils.WindowFocusChangedListener
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes.getNightMode

internal class BiometricPromptCompatDialog(
    compatBuilder: BiometricPromptCompat.Builder,
    isInscreenLayout: Boolean
) : AppCompatDialog(
    ContextThemeWrapper(compatBuilder.context, R.style.Theme_BiometricPromptDialog),
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
    var container: View? = null
        private set
    var rootView: View? = null
        private set
    private var focusListener: WindowFocusChangedListener? = null

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
    }

    fun makeVisible(){
        (rootView?.parent as View?)?.alpha = 1f
    }
    fun makeInvisible(){
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
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                if (isShowing) super@BiometricPromptCompatDialog.dismiss()
            }
        })
        title = rootView?.findViewById(R.id.title)
        subtitle = rootView?.findViewById(R.id.subtitle)
        description = rootView?.findViewById(R.id.description)
        status = rootView?.findViewById(R.id.status)
        negativeButton = rootView?.findViewById(android.R.id.button1)
        fingerprintIcon = rootView?.findViewById(R.id.fingerprint_icon)
        container = rootView?.findViewById(R.id.auth_content_container)

        fingerprintIcon?.setState(FingerprintIconView.State.ON, false)

        (rootView?.parent as View).setOnClickListener { cancel() }
        rootView?.setOnClickListener(null)
        val animation = AnimationUtils.loadAnimation(context, R.anim.move_in)
        (rootView?.parent as View).background = crossfader
        crossfader.startTransition(animation.duration.toInt())
        rootView?.startAnimation(animation)
    }

    fun setWindowFocusChangedListener(listener: WindowFocusChangedListener?) {
        focusListener = listener
    }

    //https://developer.android.com/preview/features/darktheme#configuration_changes
    val isNightMode: Boolean
        get() = "dark_theme" == rootView?.tag
}