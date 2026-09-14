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

package dev.skomlach.biometric.compat.impl.dialogs

import android.app.Dialog
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.ContextCompat
import androidx.core.os.BuildCompat
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.ScreenProtection
import dev.skomlach.biometric.compat.utils.WindowFocusChangedListener
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.misc.BroadcastTools
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.multiwindow.MultiWindowSupport
import dev.skomlach.common.statusbar.ColorUtil
import dev.skomlach.common.themes.monet.SystemColorScheme
import dev.skomlach.common.themes.monet.toArgb


class BiometricPromptCompatDialog : DialogFragment() {
    companion object {
        const val TAG =
            "dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl"

        fun getFragment(isInscreenLayout: Boolean): BiometricPromptCompatDialog {
            val fragment = BiometricPromptCompatDialog()
            fragment.arguments = Bundle().apply {
                this.putBoolean("isInscreenLayout", isInscreenLayout)
            }
            return fragment
        }
    }

    private var containerView: View? = null
    private var nativeStyle: NativeDialogStyle? = null
    private var nativeStyleSession: NativeDialogStyleApplier.Session? = null
    private var requestedStyleKey: String? = null
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
    var authPreview: SurfaceView? = null
        private set
    var rootView: View? = null
        private set
    private var focusListener: WindowFocusChangedListener? = null
    val isShowing: Boolean
        get() = dialog?.isShowing == true
    internal val isActive: Boolean
        get() = isShowing && !dismissStarted
    internal var bindInitialContent: (() -> Unit)? = null
    internal var onViewDestroyed: (() -> Unit)? = null
    private var wallpaperReceiverContext: Context? = null
    private var dismissDialogInterface: DialogInterface.OnDismissListener? = null
    private var cancelDialogInterface: DialogInterface.OnCancelListener? = null
    private var onShowDialogInterface: DialogInterface.OnShowListener? = null
    private var dismissStarted = false
    private var pendingEnterAnimation: OneShotPreDrawListener? = null
    private var firstDrawObserver: ViewTreeObserver? = null
    private var firstDrawListener: ViewTreeObserver.OnDrawListener? = null
    private var hostLayoutObserver: ViewTreeObserver? = null
    private val hostLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateDialogWindowSize() }

    private val wallpaperChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateMonetColorsInternal(context ?: return)
        }
    }
    private lateinit var viewModel: DialogViewModel

    override fun onCancel(dialog: DialogInterface) {
        dismissWithAnimation { cancelDialogInterface?.onCancel(dialog) }
    }

    override fun dismiss() {
        dismissWithAnimation()
    }

    private fun dismissWithAnimation(onCancel: (() -> Unit)? = null) {
        if (!isAdded || dismissStarted) return
        // Cancellation can synchronously request dismissal again through the auth callback.
        dismissStarted = true
        pendingEnterAnimation?.removeListener()
        pendingEnterAnimation = null
        try {
            onCancel?.invoke()
        } finally {
            animateDismiss()
        }
    }

    private fun animateDismiss() {
        if (isAdded) {
            val closingDialog = dialog
            val closeAction = {
                if (isAdded && dialog === closingDialog) {
                    dismissAllowingStateLoss()
                }
            }
            val animationView = (closingDialog?.window?.decorView as? ViewGroup)?.getChildAt(0)
            if (animationView != null && ViewCompat.isAttachedToWindow(animationView) && isShowing) {
                animationView.startAnimation(
                    AnimationUtils.loadAnimation(
                        animationView.context, R.anim.move_out
                    ).apply {
                        setAnimationListener(object : Animation.AnimationListener {
                            override fun onAnimationEnd(animation: Animation?) {
                                closeAction.invoke()
                            }

                            override fun onAnimationRepeat(animation: Animation?) {}

                            override fun onAnimationStart(animation: Animation?) {}
                        })
                    }
                )
            } else {
                closeAction.invoke()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_BiometricPromptDialog)
        viewModel = ViewModelProvider(requireActivity())[DialogViewModel::class.java]
        viewModel.listener.observe(this) {
            if (it) {
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
        this.cancelDialogInterface = dialogInterface
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
        rootView = containerView?.findViewById(R.id.dialogContent)
        title = rootView?.findViewById(R.id.title)
        subtitle = rootView?.findViewById(R.id.subtitle)
        description = rootView?.findViewById(R.id.description)
        status = rootView?.findViewById(R.id.status)
        negativeButton = rootView?.findViewById(android.R.id.button1)
        fingerprintIcon = rootView?.findViewById(R.id.fingerprint_icon)
        authPreview = rootView?.findViewById(R.id.auth_preview)
        // Never wait for resource I/O or change the profile during the initial enter animation.
        SystemBiometricDialogResources.warmUp(requireActivity())
        // Zero means MATCH_PARENT to this helper, not a resolved pixel width.
        val availableWidth = MultiWindowSupport.get(requireActivity()).resolveDialogWidth(Int.MAX_VALUE)
        nativeStyle = SystemBiometricDialogResources.cached(requireActivity())?.takeIf { it.fitsWindow(availableWidth) }
        nativeStyleSession = NativeDialogStyleApplier.Session(containerView!!)
        nativeStyle?.let {
            nativeStyleSession?.apply(it, arguments?.getBoolean("isInscreenLayout") == true)
        }
        // wrap_content must see the initial text and visibility before its first measurement.
        bindInitialContent?.invoke()
        authPreview?.layoutParams?.takeIf { nativeStyle?.modern == null }?.let {
            val params = it as FrameLayout.LayoutParams
            val view = rootView?.findViewById<FrameLayout>(R.id.auth_content_container)
            view?.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            params.height = view?.measuredHeight ?: params.height
            authPreview?.layoutParams = params
        }
        rootView?.setOnClickListener(null)

        return containerView
    }

    private fun unregisterWallpaperReceiver() {
        val context = wallpaperReceiverContext ?: return
        wallpaperReceiverContext = null
        try { BroadcastTools.unregisterGlobalBroadcastIntent(context, wallpaperChangedReceiver) }
        catch (error: Throwable) { e(error, "setupMonet") }
    }

    override fun onDestroyView() {
        onViewDestroyed?.invoke()
        unregisterWallpaperReceiver()
        pendingEnterAnimation?.removeListener()
        pendingEnterAnimation = null
        clearFirstDrawListener()
        hostLayoutObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(hostLayoutListener)
        hostLayoutObserver = null
        authPreview?.holder?.surface?.release()
        containerView?.clearAnimation()
        negativeButton?.setOnClickListener(null)
        title = null
        subtitle = null
        description = null
        status = null
        negativeButton = null
        fingerprintIcon = null
        authPreview = null
        rootView = null
        nativeStyle = null
        nativeStyleSession = null
        requestedStyleKey = null
        containerView = null
        focusListener = null
        super.onDestroyView()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        dismissStarted = false
        return object : AppCompatDialog(ContextThemeWrapper(requireContext(), theme), theme) {
            override fun cancel() {
                // Dialog.cancel() dismisses the window before OnCancelListener is delivered.
                this@BiometricPromptCompatDialog.onCancel(this)
            }
        }.apply {
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
            ScreenProtection.applyProtectionInWindow(window)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SystemBiometricDialogResources.updates.observe(viewLifecycleOwner) {
            val host = activity ?: return@observe
            // LiveData may coalesce completions of several concurrent configuration prefetches.
            // Always inspect the current key, not only the last completion's key.
            if (!dismissStarted && requestedStyleKey != null &&
                requestedStyleKey == SystemBiometricDialogResources.configurationKey(host) &&
                SystemBiometricDialogResources.hasCachedResult(host)) {
                requestedStyleKey = null
                refreshNativeStyle()
            }
        }
        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val context = context ?: return@LifecycleEventObserver
                    try {
                        if (wallpaperReceiverContext == null) {
                            BroadcastTools.registerGlobalBroadcastIntent(
                                context, wallpaperChangedReceiver,
                                IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
                            )
                            wallpaperReceiverContext = context
                        }
                        updateMonetColorsInternal(context)
                    } catch (error: Throwable) { e(error, "setupMonet") }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_DESTROY -> unregisterWallpaperReceiver()
                else -> Unit
            }
        })
        hostLayoutObserver = requireActivity().window.decorView.viewTreeObserver.also {
            it.addOnGlobalLayoutListener(hostLayoutListener)
        }
        updateDialogWindowSize()
        observeFirstContentDraw(view)
        // This one-shot callback allows the frame to draw; it never waits for OnShow/auth.
        pendingEnterAnimation = view.doOnPreDraw {
            pendingEnterAnimation = null
            if (!isActive || this.view !== view) return@doOnPreDraw
            val animationView = (dialog?.window?.decorView as? ViewGroup)?.getChildAt(0)
                ?: return@doOnPreDraw
            animationView.startAnimation(
                AnimationUtils.loadAnimation(animationView.context, R.anim.move_in).apply {
                    setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationEnd(animation: Animation?) {
                            if (isActive && this@BiometricPromptCompatDialog.view === view) {
                                logDialogTiming("enter_animation_end")
                            }
                        }

                        override fun onAnimationRepeat(animation: Animation?) {}
                        override fun onAnimationStart(animation: Animation?) {}
                    })
                }
            )
        }
        dialog?.let {
            it.setOnDismissListener(dismissDialogInterface)
        }
    }

    private fun observeFirstContentDraw(view: View) {
        if (!BiometricLoggerImpl.DEBUG) return
        firstDrawObserver = view.viewTreeObserver
        firstDrawListener = object : ViewTreeObserver.OnDrawListener {
            private var recorded = false

            override fun onDraw() {
                if (recorded) return
                recorded = true
                // Draw dispatch on the UI thread, not GPU completion or display presentation.
                if (isActive) logDialogTiming("first_content_draw")
                view.post {
                    if (firstDrawListener === this) clearFirstDrawListener()
                }
            }
        }
        firstDrawObserver?.addOnDrawListener(firstDrawListener)
    }

    private fun clearFirstDrawListener() {
        firstDrawListener?.let { listener ->
            firstDrawObserver?.takeIf { it.isAlive }?.removeOnDrawListener(listener)
        }
        firstDrawListener = null
        firstDrawObserver = null
    }

    private fun logDialogTiming(event: String) {
        BiometricLoggerImpl.d {
            val content = rootView?.findViewById<View>(R.id.dialogLayout)
            "BiometricDialogTiming: $event uptimeMs=${SystemClock.uptimeMillis()} " +
                    "dialog=${System.identityHashCode(this)} width=${content?.width} height=${content?.height} " +
                    "style=${nativeStyle?.source ?: "fallback"}"
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val host = activity ?: return
        requestedStyleKey = SystemBiometricDialogResources.configurationKey(host)
        refreshNativeStyle()
        SystemBiometricDialogResources.warmUp(host)
    }

    private fun refreshNativeStyle() {
        val host = activity ?: return
        val width = MultiWindowSupport.get(host).resolveDialogWidth(Int.MAX_VALUE)
        val style = SystemBiometricDialogResources.cached(host)?.takeIf { it.fitsWindow(width) }
        if (style != nativeStyle) {
            nativeStyle = style
            nativeStyleSession?.apply(style, arguments?.getBoolean("isInscreenLayout") == true)
            updateMonetColorsInternal(host)
        }
        updateDialogWindowSize()
    }

    private fun updateDialogWindowSize() {
        val host = activity ?: return
        val window = dialog?.window ?: return
        val support = MultiWindowSupport.get(host)
        nativeStyleSession?.limitHeight(support.currentWindowSize().y)
        val defaultWidth = resources.getDimensionPixelSize(R.dimen.dialog_width)
        val width = nativeStyle?.windowWidth(support.resolveDialogWidth(Int.MAX_VALUE), defaultWidth)
            ?: support.resolveDialogWidth(defaultWidth)
        val gravity = if (arguments?.getBoolean("isInscreenLayout") == true) Gravity.BOTTOM
            else nativeStyle?.gravity ?: Gravity.BOTTOM
        val attributes = window.attributes
        if (attributes.width == width && attributes.height == WindowManager.LayoutParams.WRAP_CONTENT &&
            attributes.gravity == gravity) return
        attributes.width = width
        attributes.height = WindowManager.LayoutParams.WRAP_CONTENT
        attributes.gravity = gravity
        window.attributes = attributes
    }

    fun <T : View?> findViewById(id: Int): T? {
        return dialog?.findViewById<T>(id)
    }

    internal fun updateMonetColorsInternal(context: Context) {
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
                e(e, "Monet colors")
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
                e(e, "Monet colors")
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
        e("MonetColor $name = $swatch[$k]; distance=$distance")
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
}
