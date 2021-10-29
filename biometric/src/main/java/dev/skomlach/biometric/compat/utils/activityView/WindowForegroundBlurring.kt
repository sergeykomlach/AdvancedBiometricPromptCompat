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
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.DialogMainColor
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.statusbar.ColorUtil
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.misc.Utils
import java.util.*

class WindowForegroundBlurring(
    private val compatBuilder: BiometricPromptCompat.Builder,
    private val parentView: ViewGroup,
    private val forceToCloseCallback: ActivityViewWatcher.ForceToCloseCallback
) : IconStateHelper.IconStateListener {
    private val context = compatBuilder.getContext()
    private var contentView: ViewGroup? = null
    private var v: View? = null
    private var renderEffect: RenderEffect? = null
    private var isAttached = false
    private var drawingInProgress = false
    private var biometricsLayout: View? = null
    private var defaultColor = ContextCompat.getColor(
        context,
        DialogMainColor.getColor(!DarkLightThemes.isNightMode(context))
    )

    private val list: List<BiometricType> by lazy {
        ArrayList<BiometricType>(compatBuilder.getAllAvailableTypes())
    }

    private val attachStateChangeListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View?) {
            BiometricLoggerImpl.d("ActivityViewWatcher.onViewAttachedToWindow")
        }

        override fun onViewDetachedFromWindow(v: View?) {
            BiometricLoggerImpl.d("ActivityViewWatcher.onViewDetachedFromWindow")
            resetListeners()
            forceToCloseCallback.onCloseBiometric()
        }
    }

    private val onDrawListener = ViewTreeObserver.OnPreDrawListener {
        updateBackground()
        true
    }

    init {
        val isDark = DarkLightThemes.isNightMode(compatBuilder.getContext())
        defaultColor = ContextCompat.getColor(
            context,
            DialogMainColor.getColor(isDark)
        )
        for (i in 0 until parentView.childCount) {
            val v = parentView.getChildAt(i)
            if (v is ViewGroup) {
                contentView = v
            }
        }
    }

    private fun updateBackground() {
        if (!isAttached || drawingInProgress)
            return
        BiometricLoggerImpl.d("ActivityViewWatcher.updateBackground")
        try {
            contentView?.let {
                BlurUtil.takeScreenshotAndBlur(
                    it,
                    object : BlurUtil.OnPublishListener {
                        override fun onBlurredScreenshot(
                            originalBitmap: Bitmap,
                            blurredBitmap: Bitmap?
                        ) {
                            updateDefaultColor(originalBitmap)
                            setDrawable(blurredBitmap)
                        }
                    })
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setDrawable(bm: Bitmap?) {
        if (!isAttached || drawingInProgress)
            return
        BiometricLoggerImpl.d("ActivityViewWatcher.setDrawable")
        drawingInProgress = true
        try {
            v?.let {
                if (Utils.isAtLeastS) {
                    contentView?.setRenderEffect(renderEffect)
                } else
                    ViewCompat.setBackground(it, BitmapDrawable(it.resources, bm))
            } ?: run {
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
                            renderEffect =
                                RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.DECAL)
                            contentView?.setRenderEffect(renderEffect)
                        } else
                            ViewCompat.setBackground(this, BitmapDrawable(this.resources, bm))
                        updateBiometricIconsLayout()
                        parentView.addView(this)

                    }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        updateIcons()
        v?.post {
            drawingInProgress = false
        }
    }

    fun setupListeners() {
        BiometricLoggerImpl.d("ActivityViewWatcher.setupListeners")
        isAttached = true
        IconStateHelper.registerListener(this)
        try {
            updateBackground()
            parentView.addOnAttachStateChangeListener(attachStateChangeListener)
            parentView.viewTreeObserver.addOnPreDrawListener(onDrawListener)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    fun resetListeners() {
        BiometricLoggerImpl.d("ActivityViewWatcher.resetListeners")
        isAttached = false
        IconStateHelper.unregisterListener(this)
        try {
            parentView.removeOnAttachStateChangeListener(attachStateChangeListener)
            parentView.viewTreeObserver.removeOnPreDrawListener(onDrawListener)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        try {
            updateIcons()
            if (Utils.isAtLeastS) {
                contentView?.setRenderEffect(null)
            }
            v?.let {
                parentView.removeView(it)
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    private fun updateBiometricIconsLayout() {
        try {
            biometricsLayout?.let { biometrics_layout ->
                biometrics_layout.findViewById<View>(R.id.face)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_FACE)) View.VISIBLE else View.GONE
                    tag = IconStates.WAITING
                }
                biometrics_layout.findViewById<View>(R.id.iris)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_IRIS)) View.VISIBLE else View.GONE
                    tag = IconStates.WAITING
                }
                biometrics_layout.findViewById<View>(R.id.fingerprint)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_FINGERPRINT)) View.VISIBLE else View.GONE
                    tag = IconStates.WAITING
                }
                biometrics_layout.findViewById<View>(R.id.heartrate)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_HEARTRATE)) View.VISIBLE else View.GONE
                    tag = IconStates.WAITING
                }
                biometrics_layout.findViewById<View>(R.id.voice)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_VOICE)) View.VISIBLE else View.GONE
                    tag = IconStates.WAITING
                }
                biometrics_layout.findViewById<View>(R.id.palm)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_PALMPRINT)) View.VISIBLE else View.GONE
                    tag = IconStates.WAITING
                }
                biometrics_layout.findViewById<View>(R.id.typing)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_BEHAVIOR)) View.VISIBLE else View.GONE
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

    private fun getResizedBitmap(bm: Bitmap?, newHeight: Int, newWidth: Int): Bitmap? {
        if (bm == null) return bm
        if (newHeight <= 0 || newWidth <= 0) return null
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        // CREATE A MATRIX FOR THE MANIPULATION
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight)

        // "RECREATE" THE NEW BITMAP
        return Bitmap.createBitmap(
            bm, 0, 0, width, height,
            matrix, true
        )
    }

    private fun updateDefaultColor(bm: Bitmap) {

        try {
            var b = Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height / 2)
            b = getResizedBitmap(getResizedBitmap(b, b.width / 2, b.height / 2), 1, 1)
            val isDark = ColorUtil.trueDarkColor(b.getPixel(0, 0))
            defaultColor = ContextCompat.getColor(
                context,
                DialogMainColor.getColor(isDark)
            )
            BiometricLoggerImpl.d(
                "ActivityViewWatcher.updateDefaultColor isDark - $isDark; color - ${
                    Integer.toHexString(
                        defaultColor
                    )
                }"
            )
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    private fun updateIcons() {
        try {
            biometricsLayout?.let { biometrics_layout ->

                for (type in BiometricType.values()) {
                    when (type) {
                        BiometricType.BIOMETRIC_FACE -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.face)?.tag as IconStates
                        )
                        BiometricType.BIOMETRIC_IRIS -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.iris)?.tag as IconStates
                        )
                        BiometricType.BIOMETRIC_HEARTRATE -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.heartrate)?.tag as IconStates
                        )
                        BiometricType.BIOMETRIC_VOICE -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.voice)?.tag as IconStates
                        )
                        BiometricType.BIOMETRIC_PALMPRINT -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.palm)?.tag as IconStates
                        )
                        BiometricType.BIOMETRIC_BEHAVIOR -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.typing)?.tag as IconStates
                        )
                        BiometricType.BIOMETRIC_FINGERPRINT -> setIconState(
                            type,
                            biometrics_layout.findViewById<View>(R.id.fingerprint)?.tag as IconStates
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    override fun onError(type: BiometricType?) {
        setIconState(type, IconStates.ERROR)
    }

    override fun onSuccess(type: BiometricType?) {
        setIconState(type, IconStates.SUCCESS)
    }

    override fun reset(type: BiometricType?) {
        setIconState(type, IconStates.WAITING)
    }

    private fun setIconState(type: BiometricType?, iconStates: IconStates) {
        try {
            biometricsLayout?.let { biometrics_layout ->
                val color = when (iconStates) {
                    IconStates.WAITING -> defaultColor
                    IconStates.ERROR -> Color.RED
                    IconStates.SUCCESS -> Color.GREEN
                }
                biometrics_layout.findViewById<View>(R.id.biometric_divider)
                    .setBackgroundColor(defaultColor)

                when (type) {
                    BiometricType.BIOMETRIC_FACE -> {
                        biometrics_layout.findViewById<View>(R.id.face)?.tag = iconStates
                        ImageViewCompat.setImageTintList(
                            biometrics_layout.findViewById<ImageView>(R.id.face),
                            ColorStateList.valueOf(color)
                        )
                    }
                    BiometricType.BIOMETRIC_IRIS -> {
                        biometrics_layout.findViewById<View>(R.id.iris)?.tag = iconStates
                        ImageViewCompat.setImageTintList(
                            biometrics_layout.findViewById<ImageView>(R.id.iris),
                            ColorStateList.valueOf(color)
                        )
                    }
                    BiometricType.BIOMETRIC_HEARTRATE -> {
                        biometrics_layout.findViewById<View>(R.id.heartrate)?.tag = iconStates
                        ImageViewCompat.setImageTintList(
                            biometrics_layout.findViewById<ImageView>(R.id.heartrate),
                            ColorStateList.valueOf(color)
                        )
                    }
                    BiometricType.BIOMETRIC_VOICE -> {
                        biometrics_layout.findViewById<View>(R.id.voice)?.tag = iconStates
                        ImageViewCompat.setImageTintList(
                            biometrics_layout.findViewById<ImageView>(R.id.voice),
                            ColorStateList.valueOf(color)
                        )
                    }
                    BiometricType.BIOMETRIC_PALMPRINT -> {
                        biometrics_layout.findViewById<View>(R.id.palm)?.tag = iconStates
                        ImageViewCompat.setImageTintList(
                            biometrics_layout.findViewById<ImageView>(R.id.palm),
                            ColorStateList.valueOf(color)
                        )
                    }
                    BiometricType.BIOMETRIC_BEHAVIOR -> {
                        biometrics_layout.findViewById<View>(R.id.typing)?.tag = iconStates
                        ImageViewCompat.setImageTintList(
                            biometrics_layout.findViewById<ImageView>(R.id.typing),
                            ColorStateList.valueOf(color)
                        )
                    }
                    BiometricType.BIOMETRIC_FINGERPRINT -> {
                        biometrics_layout.findViewById<View>(R.id.fingerprint)?.tag = iconStates
                        ImageViewCompat.setImageTintList(
                            biometrics_layout.findViewById<ImageView>(R.id.fingerprint),
                            ColorStateList.valueOf(color)
                        )
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