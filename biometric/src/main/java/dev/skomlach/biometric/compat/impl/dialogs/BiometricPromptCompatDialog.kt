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

import android.app.Dialog
import android.app.UiModeManager
import android.content.*
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.Button
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.ContextCompat
import androidx.core.os.BuildCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.WindowFocusChangedListener
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.biometric.compat.utils.monet.SystemColorScheme
import dev.skomlach.biometric.compat.utils.monet.toArgb
import dev.skomlach.biometric.compat.utils.statusbar.ColorUtil
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.misc.Utils


class BiometricPromptCompatDialog : DialogFragment() {
    companion object {
        const val TAG = "dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl"
        fun getFragment(isInscreenLayout: Boolean): BiometricPromptCompatDialog {
            val fragment = BiometricPromptCompatDialog()
            fragment.arguments = Bundle().apply {
                this.putBoolean("isInscreenLayout", isInscreenLayout)
            }
            return fragment
        }
    }

    private var containerView: View? = null
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
    val isShowing: Boolean
        get() = dialog?.isShowing ?: false
    private var dismissDialogInterface: DialogInterface.OnDismissListener? = null
    private var cancelDialogInterface: DialogInterface.OnCancelListener? = null
    private var onShowDialogInterface: DialogInterface.OnShowListener? = null

    private val wallpaperChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateMonetColorsInternal(context ?: return)
        }
    }
    private lateinit var viewModel : DialogViewModel
    override fun dismiss() {
        if (isAdded) {
            val fragmentManager = parentFragmentManager
            val dialogFragment = fragmentManager.findFragmentByTag(
                TAG
            ) as BiometricPromptCompatDialog?
            if (dialogFragment != null) {
                if (dialogFragment.isAdded) {
                    dialogFragment.dismissAllowingStateLoss()
                } else {
                    fragmentManager.beginTransaction().remove(dialogFragment)
                        .commitAllowingStateLoss()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_BiometricPromptDialog)
        viewModel = ViewModelProvider(requireActivity())[DialogViewModel::class.java]
        viewModel.listener.observe(this) {
            if(it) {
                dismiss()
            }
        }
    }

    fun makeVisible() {
        containerView?.alpha = 1f
    }

    fun makeInvisible() {
        containerView?.alpha = 0.01f
    }

    fun setOnDismissListener(dialogInterface: DialogInterface.OnDismissListener) {
        dialog?.setOnDismissListener(dialogInterface) ?: run {
            this.dismissDialogInterface = dialogInterface
        }
    }

    fun setOnCancelListener(dialogInterface: DialogInterface.OnCancelListener) {
        dialog?.setOnCancelListener(dialogInterface) ?: run {
            this.cancelDialogInterface = dialogInterface
        }
    }

    fun setOnShowListener(dialogInterface: DialogInterface.OnShowListener) {
        dialog?.setOnShowListener(dialogInterface) ?: run {
            this.onShowDialogInterface = dialogInterface
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        @LayoutRes
        val res: Int =
            if (arguments?.getBoolean("isInscreenLayout") == true) R.layout.biometric_prompt_dialog_content_inscreen else
                R.layout.biometric_prompt_dialog_content
        containerView = inflater.inflate(
            res,
            container,
            false
        )
        var lastKnownFocus: Boolean? = null
        containerView?.viewTreeObserver?.addOnGlobalLayoutListener {
            d("WindowFocusChangedListenerDialog.OnGlobalLayoutListener called")
            val root = findViewById<View>(Window.ID_ANDROID_CONTENT)
            val hasFocus = root?.hasWindowFocus()
            if (hasFocus == lastKnownFocus)
                return@addOnGlobalLayoutListener
            lastKnownFocus = hasFocus
            e("WindowFocusChangedListenerDialog.hasFocus(1) - $hasFocus")
            if (focusListener != null) {
                if (root != null) {
                    if (ViewCompat.isAttachedToWindow(root)) {
                        focusListener?.hasFocus(root.hasWindowFocus())
                    }
                }
            }
            root?.context?.let {
                updateMonetColorsInternal(it)
            }

        }
        rootView = containerView?.findViewById(R.id.dialogContent)
        rootView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                try {
                    @Suppress("DEPRECATION")
                    v.context?.registerReceiver(
                        wallpaperChangedReceiver,
                        IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
                    )
                    updateMonetColorsInternal(v.context ?: return)
                } catch (e: Throwable) {
                    BiometricLoggerImpl.e(e, "setupMonet")
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                try {
                    v.context.unregisterReceiver(wallpaperChangedReceiver)
                } catch (e: Throwable) {
                    BiometricLoggerImpl.e(e, "setupMonet")
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

        rootView?.setOnClickListener(null)

        return containerView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AppCompatDialog(ContextThemeWrapper(requireContext(), theme), theme).apply {
            val currentMode = DarkLightThemes.getNightModeCompatWithInscreen(context)
            val NIGHT_MODE = if (currentMode == UiModeManager.MODE_NIGHT_YES) {
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
            this.setCanceledOnTouchOutside(true)
            this.setOnShowListener(onShowDialogInterface)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.let {
            it.window?.let { w ->
                val wlp = w.attributes
                wlp.height = containerView?.apply {
                    this.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                }?.measuredHeight ?: WindowManager.LayoutParams.WRAP_CONTENT
                wlp.gravity = Gravity.BOTTOM
                w.attributes = wlp
                ScreenProtection().applyProtectionInWindow(w)
            }
            it.setOnCancelListener(cancelDialogInterface)
            it.setOnDismissListener(dismissDialogInterface)
        }
    }

    fun <T : View?> findViewById(id: Int): T? {
        return dialog?.findViewById<T>(id)
    }

    private fun updateMonetColorsInternal(context: Context) {
        if (Utils.isAtLeastT) {
//            E: [MonetColor api33_finger_bg = neutral1[700]; distance=0.02147931470032775]
//            E: [MonetColor api33_finger_lines = accent1[100]; distance=0.03441162299750387]

//            E: [MonetColor status_night = neutral1[200]; distance=0.016637805624463507]
//            E: [MonetColor button_night = accent1[100]; distance=0.005545935208154502]

//            E: [MonetColor status_day = neutral1[500]; distance=0.01616904070533888]
//            E: [MonetColor button_day = accent1[600]; distance=0.005545935208154502]
            val negativeButtonColor = ContextCompat.getColor(
                context,
                if (Utils.isAtLeastS) R.color.material_blue_500 else R.color.material_deep_teal_500
            )

            val textColor = ContextCompat.getColor(context, R.color.textColor)

            try {
                val monetColors = SystemColorScheme()
                if (DarkLightThemes.isNightModeCompatWithInscreen(context)) {
                    fingerprintIcon?.tintColor(monetColors.accent1[100]?.toArgb())
                    status?.setTextColor(
                        monetColors.neutral1[200]?.toArgb() ?: textColor
                    )
                    negativeButton?.setTextColor(
                        monetColors.accent1[100]?.toArgb() ?: negativeButtonColor
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
                    fingerprintIcon?.tintColor(monetColors.accent1[100]?.toArgb())
                    negativeButton?.setTextColor(
                        monetColors.accent1[600]?.toArgb() ?: negativeButtonColor
                    )
                    status?.setTextColor(
                        monetColors.neutral1[500]?.toArgb() ?: textColor
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

        } else if (Utils.isAtLeastS) {
            val negativeButtonColor = ContextCompat.getColor(
                context,
                if (Utils.isAtLeastS) R.color.material_blue_500 else R.color.material_deep_teal_500
            )

            val textColor = ContextCompat.getColor(context, R.color.textColor)

            try {
                val monetColors = SystemColorScheme()
                if (DarkLightThemes.isNightModeCompatWithInscreen(context)) {
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun findNearestColor(name: String, color: Int) {
        var k: Int? = null
        var swatch: String? = null
        var distance: Double = Double.MAX_VALUE
        val monetColors = SystemColorScheme()
        for (key in monetColors.accent1.keys) {
            val value = monetColors.accent1[key] ?: continue
            val d = ColorUtil.colorDistance(value.toArgb(), color)
            if (d <= distance) {
                distance = d
                k = key
                swatch = "accent1"
            }
        }
        for (key in monetColors.accent2.keys) {
            val value = monetColors.accent2[key] ?: continue
            val d = ColorUtil.colorDistance(value.toArgb(), color)
            if (d <= distance) {
                distance = d
                k = key
                swatch = "accent2"
            }
        }
        for (key in monetColors.accent3.keys) {
            val value = monetColors.accent3[key] ?: continue
            val d = ColorUtil.colorDistance(value.toArgb(), color)
            if (d <= distance) {
                distance = d
                k = key
                swatch = "accent3"
            }
        }

        for (key in monetColors.neutral1.keys) {
            val value = monetColors.neutral1[key] ?: continue
            val d = ColorUtil.colorDistance(value.toArgb(), color)
            if (d <= distance) {
                distance = d
                k = key
                swatch = "neutral1"
            }
        }
        for (key in monetColors.neutral2.keys) {
            val value = monetColors.neutral2[key] ?: continue
            val d = ColorUtil.colorDistance(value.toArgb(), color)
            if (d <= distance) {
                distance = d
                k = key
                swatch = "neutral2"
            }
        }
        BiometricLoggerImpl.e("MonetColor $name = $swatch[$k]; distance=$distance")
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
}