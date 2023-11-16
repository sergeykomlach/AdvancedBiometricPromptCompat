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
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.palette.graphics.Palette
import dev.skomlach.biometric.compat.*
import dev.skomlach.biometric.compat.utils.DialogMainColor
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.blur.BlurUtil
import dev.skomlach.common.blur.DEFAULT_RADIUS
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.statusbar.ColorUtil
import java.util.concurrent.atomic.AtomicBoolean


class WindowForegroundBlurring(
    private val compatBuilder: BiometricPromptCompat.Builder,
    private val parentView: ViewGroup,
    private val forceToCloseCallback: ActivityViewWatcher.ForceToCloseCallback
) : IconStateHelper.IconStateListener {
    private val context = compatBuilder.getContext()
    private var contentView: ViewGroup? = null
    private var v: View? = null
    private var renderEffect: RenderEffect? = null

    @Volatile
    private var isAttached = false
    private var drawingInProgress = AtomicBoolean(false)
    private var biometricsLayout: View? = null
    private var defaultColor = Color.TRANSPARENT


    private val list: List<BiometricType>
        get() {
            return (if (compatBuilder.isBackgroundBiometricIconsEnabled()) ArrayList<BiometricType>(
                compatBuilder.getAllAvailableTypes()
            ) else emptyList()).filter {
                BiometricManagerCompat.isBiometricReadyForUsage(
                    BiometricAuthRequest(
                        compatBuilder.getBiometricAuthRequest().api,
                        type = it
                    )
                )
            }
        }

    private val attachStateChangeListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            BiometricLoggerImpl.d("${this.javaClass.name}.onViewAttachedToWindow")
        }

        override fun onViewDetachedFromWindow(v: View) {
            BiometricLoggerImpl.d("${this.javaClass.name}.onViewDetachedFromWindow")
            forceToCloseCallback.onCloseBiometric()
        }
    }

    private val onDrawListener = ViewTreeObserver.OnPreDrawListener {
        updateBackground()
        true
    }

    init {
        val isDark = DarkLightThemes.isNightMode(compatBuilder.getContext())
        defaultColor =
            DialogMainColor.getColor(context, !isDark)
        BiometricLoggerImpl.e(
            "${this.javaClass.name}.updateDefaultColor isDark -  ${ColorUtil.isDark(defaultColor)}; color - ${
                Integer.toHexString(
                    defaultColor
                )
            }"
        )

        for (i in 0 until parentView.childCount) {
            val v = parentView.getChildAt(i)
            if (v is ViewGroup) {
                contentView = v
            }
        }
        ExecutorHelper.post {
            @SuppressLint("ClickableViewAccessibility")
            v = LayoutInflater.from(ContextWrapper(context))
                .inflate(R.layout.blurred_screen, null, false).apply {
                    tag = tag
                    alpha = 1f
                    biometricsLayout = findViewById(R.id.biometrics_layout)
                    isFocusable = true
                    isClickable = true
                    isLongClickable = true
                    setOnTouchListener { _, _ ->
                        true
                    }
                    if (Utils.isAtLeastS) {
                        if (renderEffect == null)
                            renderEffect =
                                RenderEffect.createBlurEffect(
                                    DEFAULT_RADIUS.toFloat(),
                                    DEFAULT_RADIUS.toFloat(),
                                    Shader.TileMode.DECAL
                                )
                        contentView?.setRenderEffect(renderEffect)
                    } else
                        ViewCompat.setBackground(this, ColorDrawable(Color.TRANSPARENT))
                }
        }
    }

    private fun updateBackground() {
        if (!isAttached)
            return
        if (!drawingInProgress.get()) {
            drawingInProgress.set(true)

            BiometricLoggerImpl.d("${this.javaClass.name}.updateBackground")
            try {
                contentView?.let {
                    BlurUtil.takeScreenshotAndBlur(
                        it,
                        object : BlurUtil.OnPublishListener {
                            override fun onBlurredScreenshot(
                                originalBitmap: Bitmap,
                                blurredBitmap: Bitmap?
                            ) {
                                if (!isAttached)
                                    return
                                setDrawable(blurredBitmap)
                                updateDefaultColor(originalBitmap)
                            }
                        })
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
    }


    private fun setDrawable(bm: Bitmap?) {
        BiometricLoggerImpl.d("${this.javaClass.name}.setDrawable")
        try {
            v?.let {
                if (Utils.isAtLeastS) {
                    if (renderEffect == null)
                        renderEffect =
                            RenderEffect.createBlurEffect(
                                DEFAULT_RADIUS.toFloat(),
                                DEFAULT_RADIUS.toFloat(),
                                Shader.TileMode.DECAL
                            )
                    contentView?.setRenderEffect(renderEffect)
                } else
                    ViewCompat.setBackground(it, BitmapDrawable(it.resources, bm))
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        ExecutorHelper.postDelayed({
            updateBiometricIconsLayout()
            drawingInProgress.set(false)
        }, context.resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
    }

    fun setupListeners() {
        if (isAttached) return
        isAttached = true
        try {
            v?.apply {
                parentView.addView(this)
            }
            updateBiometricIconsLayout()

            updateBackground()
            IconStateHelper.registerListener(this)
            parentView.findViewTreeLifecycleOwner()?.lifecycle?.addObserver(object :
                LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    if(event == Lifecycle.Event.ON_DESTROY){
                        forceToCloseCallback.onCloseBiometric()
                    }
                }
            })
            parentView.addOnAttachStateChangeListener(attachStateChangeListener)
            parentView.viewTreeObserver.addOnPreDrawListener(onDrawListener)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        BiometricLoggerImpl.d("${this.javaClass.name}.setupListeners")

    }

    fun resetListeners() {
        if (!isAttached) return
        isAttached = false

        try {
            v?.let {
                parentView.removeView(it)
            }
            parentView.viewTreeObserver.removeOnPreDrawListener(onDrawListener)
            parentView.removeOnAttachStateChangeListener(attachStateChangeListener)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        } finally {
            try {
                if (Utils.isAtLeastS) {
                    contentView?.setRenderEffect(null)
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
        IconStateHelper.unregisterListener(this)
        BiometricLoggerImpl.d("${this.javaClass.name}.resetListeners")

    }

    private fun updateBiometricIconsLayout() {
        BiometricLoggerImpl.d("${this.javaClass.name}.updateBiometricIconsLayout")
        try {
            biometricsLayout?.let { biometrics_layout ->
                biometrics_layout.findViewById<View>(R.id.face)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_FACE)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }
                biometrics_layout.findViewById<View>(R.id.iris)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_IRIS)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }
                biometrics_layout.findViewById<View>(R.id.fingerprint)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_FINGERPRINT)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }
                biometrics_layout.findViewById<View>(R.id.heartrate)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_HEARTRATE)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }
                biometrics_layout.findViewById<View>(R.id.voice)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_VOICE)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }
                biometrics_layout.findViewById<View>(R.id.palm)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_PALMPRINT)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }
                biometrics_layout.findViewById<View>(R.id.typing)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_BEHAVIOR)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }

                updateIcons()
                if (list.isEmpty()) {
                    biometrics_layout.visibility = View.GONE
                } else {
                    biometrics_layout.visibility = View.VISIBLE
                }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    private fun updateDefaultColor(bm: Bitmap) {
        BiometricLoggerImpl.d("${this.javaClass.name}.updateDefaultColor")
        try {
            Palette.from(bm).generate { palette ->
                try {
                    var isDark = DarkLightThemes.isNightMode(compatBuilder.getContext())
                    val defColor =
                        DialogMainColor.getColor(context, !isDark)
                    BiometricLoggerImpl.d(
                        "${this.javaClass.name}.updateDefaultColor#0 isDark -  ${
                            ColorUtil.isDark(
                                defColor
                            )
                        }; color - ${
                            Integer.toHexString(
                                defColor
                            )
                        }"
                    )
                    defaultColor =
                        palette?.getVibrantColor(defColor)?.let { color ->
                            isDark = ColorUtil.isDark(color)
                            DialogMainColor.getColor(context, isDark)
                        } ?: defColor
                    BiometricLoggerImpl.d(
                        "${this.javaClass.name}.updateDefaultColor#2 isDark - ${
                            ColorUtil.isDark(
                                defaultColor
                            )
                        }; color - ${
                            Integer.toHexString(
                                defaultColor
                            )
                        }"
                    )
                    updateIcons()
                } catch (e: Throwable) {
                    BiometricLoggerImpl.e(e)
                }
            }

        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    private fun updateIcons() {
        BiometricLoggerImpl.d("${this.javaClass.name}.updateIcons")
        try {
            biometricsLayout?.let { biometrics_layout ->

                for (type in BiometricType.values()) {
                    when (type) {
                        BiometricType.BIOMETRIC_FACE -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.face)?.tag as IconStates?
                        )

                        BiometricType.BIOMETRIC_IRIS -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.iris)?.tag as IconStates?
                        )

                        BiometricType.BIOMETRIC_HEARTRATE -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.heartrate)?.tag as IconStates?
                        )

                        BiometricType.BIOMETRIC_VOICE -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.voice)?.tag as IconStates?
                        )

                        BiometricType.BIOMETRIC_PALMPRINT -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.palm)?.tag as IconStates?
                        )

                        BiometricType.BIOMETRIC_BEHAVIOR -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.typing)?.tag as IconStates?
                        )

                        BiometricType.BIOMETRIC_FINGERPRINT -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.fingerprint)?.tag as IconStates?
                        )

                        else -> {
                            //no-op
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    override fun onError(type: BiometricType?) {
        updateBiometricIconsLayout()
        setIconState(type, IconStates.ERROR)
    }

    override fun onSuccess(type: BiometricType?) {
        updateBiometricIconsLayout()
        setIconState(type, IconStates.SUCCESS)
    }

    override fun reset(type: BiometricType?) {
        updateBiometricIconsLayout()
        setIconState(type, IconStates.WAITING)
    }

    private fun setIconState(type: BiometricType?, iconStates: IconStates?) {
        BiometricLoggerImpl.d("${this.javaClass.name}.setIconState $type=$iconStates")
        try {
            biometricsLayout?.let { biometrics_layout ->
                val color = if (iconStates == null) defaultColor else when (iconStates) {
                    IconStates.WAITING -> defaultColor
                    IconStates.ERROR -> Color.RED
                    IconStates.SUCCESS -> Color.GREEN
                }
                biometrics_layout.findViewById<View>(R.id.biometric_divider)
                    .setBackgroundColor(defaultColor)

                when (type) {
                    BiometricType.BIOMETRIC_FACE -> {
                        biometrics_layout.findViewById<View>(R.id.face)?.tag = iconStates
                        biometrics_layout.findViewById<ImageView>(R.id.face).setColorFilter(color)
                    }

                    BiometricType.BIOMETRIC_IRIS -> {
                        biometrics_layout.findViewById<View>(R.id.iris)?.tag = iconStates
                        biometrics_layout.findViewById<ImageView>(R.id.iris).setColorFilter(color)
                    }

                    BiometricType.BIOMETRIC_HEARTRATE -> {
                        biometrics_layout.findViewById<View>(R.id.heartrate)?.tag = iconStates
                        biometrics_layout.findViewById<ImageView>(R.id.heartrate)
                            .setColorFilter(color)
                    }

                    BiometricType.BIOMETRIC_VOICE -> {
                        biometrics_layout.findViewById<View>(R.id.voice)?.tag = iconStates
                        biometrics_layout.findViewById<ImageView>(R.id.voice).setColorFilter(color)
                    }

                    BiometricType.BIOMETRIC_PALMPRINT -> {
                        biometrics_layout.findViewById<View>(R.id.palm)?.tag = iconStates
                        biometrics_layout.findViewById<ImageView>(R.id.palm).setColorFilter(color)
                    }

                    BiometricType.BIOMETRIC_BEHAVIOR -> {
                        biometrics_layout.findViewById<View>(R.id.typing)?.tag = iconStates
                        biometrics_layout.findViewById<ImageView>(R.id.typing).setColorFilter(color)
                    }

                    BiometricType.BIOMETRIC_FINGERPRINT -> {
                        biometrics_layout.findViewById<View>(R.id.fingerprint)?.tag = iconStates
                        biometrics_layout.findViewById<ImageView>(R.id.fingerprint)
                            .setColorFilter(color)
                    }

                    else -> {
                        //no-op
                    }
                }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    enum class IconStates {
        WAITING,
        ERROR,
        SUCCESS
    }
}