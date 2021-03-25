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

package dev.skomlach.biometric.compat.utils.activityView

import android.annotation.SuppressLint
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.*
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.DialogMainColor
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import java.util.*

class ActivityViewWatcher(
    private val compatBuilder: BiometricPromptCompat.Builder,
    private val forceToCloseCallback: ForceToCloseCallback
) : IconStateHelper.IconStateListener {
    private val context = compatBuilder.context
    private val parentView: ViewGroup = context.findViewById<ViewGroup>(Window.ID_ANDROID_CONTENT)
    private lateinit var contentView: ViewGroup
    private var v: View? = null
    private var isAttached = false
    private var drawingInProgress = false
    private lateinit var biometrics_layout: View
    private val list: List<BiometricType> by lazy {
        ArrayList<BiometricType>(compatBuilder.allAvailableTypes)
    }

    private val attachStateChangeListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View?) {
            BiometricLoggerImpl.e("ActivityViewWatcher.onViewAttachedToWindow")
        }

        override fun onViewDetachedFromWindow(v: View?) {
            BiometricLoggerImpl.e("ActivityViewWatcher.onViewDetachedFromWindow")
            resetListeners()
            forceToCloseCallback.onCloseBiometric()
        }
    }

    private val onDrawListener = ViewTreeObserver.OnDrawListener { updateBackground() }

    init {
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
        BiometricLoggerImpl.e("ActivityViewWatcher.updateBackground")
        try {
            BlurUtil.takeScreenshotAndBlur(
                contentView,
                object : BlurUtil.OnPublishListener {
                    override fun onBlurredScreenshot(bm: Bitmap) {
                        setDrawable(bm)
                    }
                })
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setDrawable(bm: Bitmap) {
        if (!isAttached || drawingInProgress)
            return
        BiometricLoggerImpl.d("ActivityViewWatcher.setDrawable")
        drawingInProgress = true
        try {
            v?.let {
                ViewCompat.setBackground(it, BitmapDrawable(it.resources, bm))
            } ?: run {
                v = LayoutInflater.from(ContextWrapper(context))
                    .inflate(R.layout.blurred_screen, null, false).apply {
                        tag = tag
                        alpha = 1f
                        biometrics_layout = findViewById(R.id.biometrics_layout)
                        isFocusable = true
                        isClickable = true
                        isLongClickable = true
                        setOnTouchListener { _, _ ->
                            true
                        }
                        ViewCompat.setBackground(this, BitmapDrawable(this.resources, bm))
                        updateBiometricIconsLayout()
                        parentView.addView(this)

                    }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        v?.post {
            drawingInProgress = false
        }
    }

    fun setupListeners() {
        BiometricLoggerImpl.e("ActivityViewWatcher.setupListeners")
        isAttached = true
        IconStateHelper.registerListener(this)
        try {
            updateBackground()
            parentView.addOnAttachStateChangeListener(attachStateChangeListener)
            parentView.viewTreeObserver.addOnDrawListener(onDrawListener)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    fun resetListeners() {
        BiometricLoggerImpl.e("ActivityViewWatcher.resetListeners")
        isAttached = false
        IconStateHelper.unregisterListener(this)
        try {
            parentView.removeOnAttachStateChangeListener(attachStateChangeListener)
            parentView.viewTreeObserver.removeOnDrawListener(onDrawListener)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        try {
            v?.let {
                resetIcons()
                parentView.removeView(it)
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    private fun updateBiometricIconsLayout() {
        resetIcons()
        biometrics_layout.findViewById<View>(R.id.face)?.visibility =
            if (list.contains(BiometricType.BIOMETRIC_FACE)) View.VISIBLE else View.GONE
        biometrics_layout.findViewById<View>(R.id.iris)?.visibility =
            if (list.contains(BiometricType.BIOMETRIC_IRIS)) View.VISIBLE else View.GONE
        biometrics_layout.findViewById<View>(R.id.fingerprint)?.visibility =
            if (list.contains(BiometricType.BIOMETRIC_FINGERPRINT)) View.VISIBLE else View.GONE
        biometrics_layout.findViewById<View>(R.id.heartrate)?.visibility =
            if (list.contains(BiometricType.BIOMETRIC_HEARTRATE)) View.VISIBLE else View.GONE
        biometrics_layout.findViewById<View>(R.id.voice)?.visibility =
            if (list.contains(BiometricType.BIOMETRIC_VOICE)) View.VISIBLE else View.GONE
        biometrics_layout.findViewById<View>(R.id.palm)?.visibility =
            if (list.contains(BiometricType.BIOMETRIC_PALMPRINT)) View.VISIBLE else View.GONE
        biometrics_layout.findViewById<View>(R.id.typing)?.visibility =
            if (list.contains(BiometricType.BIOMETRIC_BEHAVIOR)) View.VISIBLE else View.GONE
        if (list.isEmpty()) {
            biometrics_layout.visibility = View.GONE
        } else {
            biometrics_layout.visibility = View.VISIBLE
        }
    }

    fun resetIcons() {
        val defaultColor = ContextCompat.getColor(
            context,
            DialogMainColor.getColor(!DarkLightThemes.isNightMode(context))
        )

        for (type in BiometricType.values()) {
            when (type) {
                BiometricType.BIOMETRIC_FACE -> if (biometrics_layout.findViewById<View>(R.id.face)?.tag == null
                ) ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.face),
                    ColorStateList.valueOf(defaultColor)
                )
                BiometricType.BIOMETRIC_IRIS -> if (biometrics_layout.findViewById<View>(R.id.iris)
                        .tag == null
                ) ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.iris),
                    ColorStateList.valueOf(defaultColor)
                )
                BiometricType.BIOMETRIC_HEARTRATE -> if (biometrics_layout.findViewById<View>(R.id.heartrate)
                        .tag == null
                ) ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.heartrate),
                    ColorStateList.valueOf(defaultColor)
                )
                BiometricType.BIOMETRIC_VOICE -> if (biometrics_layout.findViewById<View>(R.id.voice)
                        .tag == null
                ) ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.voice),
                    ColorStateList.valueOf(defaultColor)
                )
                BiometricType.BIOMETRIC_PALMPRINT -> if (biometrics_layout.findViewById<View>(R.id.palm)
                        .tag == null
                ) ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.palm),
                    ColorStateList.valueOf(defaultColor)
                )
                BiometricType.BIOMETRIC_BEHAVIOR -> if (biometrics_layout.findViewById<View>(R.id.typing)
                        .tag == null
                ) ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.typing),
                    ColorStateList.valueOf(defaultColor)
                )
                BiometricType.BIOMETRIC_FINGERPRINT -> if (biometrics_layout.findViewById<View>(R.id.fingerprint)
                        .tag == null
                ) ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.fingerprint),
                    ColorStateList.valueOf(defaultColor)
                )
            }
        }
    }

    interface ForceToCloseCallback {
        fun onCloseBiometric()
    }

    override fun onError(type: BiometricType?) {
        when (type) {
            BiometricType.BIOMETRIC_FACE -> ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.face),
                ColorStateList.valueOf(Color.RED)
            )
            BiometricType.BIOMETRIC_IRIS -> ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.iris),
                ColorStateList.valueOf(Color.RED)
            )
            BiometricType.BIOMETRIC_HEARTRATE -> ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.heartrate),
                ColorStateList.valueOf(Color.RED)
            )
            BiometricType.BIOMETRIC_VOICE -> ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.voice),
                ColorStateList.valueOf(Color.RED)
            )
            BiometricType.BIOMETRIC_PALMPRINT -> ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.palm),
                ColorStateList.valueOf(Color.RED)
            )
            BiometricType.BIOMETRIC_BEHAVIOR -> ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.typing),
                ColorStateList.valueOf(Color.RED)
            )
            BiometricType.BIOMETRIC_FINGERPRINT -> ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.fingerprint),
                ColorStateList.valueOf(Color.RED)
            )
        }
    }

    override fun onSuccess(type: BiometricType?) {
        when (type) {
            BiometricType.BIOMETRIC_FACE -> {
                biometrics_layout.findViewById<View>(R.id.face).tag = type
                ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.face),
                    ColorStateList.valueOf(Color.GREEN)
                )
            }
            BiometricType.BIOMETRIC_IRIS -> {
                biometrics_layout.findViewById<View>(R.id.iris).tag = type
                ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.iris),
                    ColorStateList.valueOf(Color.GREEN)
                )
            }
            BiometricType.BIOMETRIC_HEARTRATE -> {
                biometrics_layout.findViewById<View>(R.id.heartrate).tag = type
                ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.heartrate),
                    ColorStateList.valueOf(Color.GREEN)
                )
            }
            BiometricType.BIOMETRIC_VOICE -> {
                biometrics_layout.findViewById<View>(R.id.voice).tag = type
                ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.voice),
                    ColorStateList.valueOf(Color.GREEN)
                )
            }
            BiometricType.BIOMETRIC_PALMPRINT -> {
                biometrics_layout.findViewById<View>(R.id.palm).tag = type
                ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.palm),
                    ColorStateList.valueOf(Color.GREEN)
                )
            }
            BiometricType.BIOMETRIC_BEHAVIOR -> {
                biometrics_layout.findViewById<View>(R.id.typing).tag = type
                ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.typing),
                    ColorStateList.valueOf(Color.GREEN)
                )
            }
            BiometricType.BIOMETRIC_FINGERPRINT -> {
                biometrics_layout.findViewById<View>(R.id.fingerprint).tag = type
                ImageViewCompat.setImageTintList(
                    biometrics_layout.findViewById<ImageView>(R.id.fingerprint),
                    ColorStateList.valueOf(Color.GREEN)
                )
            }
        }
    }

    override fun reset(type: BiometricType?) {
        val defaultColor = ContextCompat.getColor(
            context,
            DialogMainColor.getColor(!DarkLightThemes.isNightMode(context))
        )
        when (type) {
            BiometricType.BIOMETRIC_FACE -> if (biometrics_layout.findViewById<View>(R.id.face)?.tag == null
            ) ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.face),
                ColorStateList.valueOf(defaultColor)
            )
            BiometricType.BIOMETRIC_IRIS -> if (biometrics_layout.findViewById<View>(R.id.iris)
                    .tag == null
            ) ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.iris),
                ColorStateList.valueOf(defaultColor)
            )
            BiometricType.BIOMETRIC_HEARTRATE -> if (biometrics_layout.findViewById<View>(R.id.heartrate)
                    .tag == null
            ) ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.heartrate),
                ColorStateList.valueOf(defaultColor)
            )
            BiometricType.BIOMETRIC_VOICE -> if (biometrics_layout.findViewById<View>(R.id.voice)
                    .tag == null
            ) ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.voice),
                ColorStateList.valueOf(defaultColor)
            )
            BiometricType.BIOMETRIC_PALMPRINT -> if (biometrics_layout.findViewById<View>(R.id.palm)
                    .tag == null
            ) ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.palm),
                ColorStateList.valueOf(defaultColor)
            )
            BiometricType.BIOMETRIC_BEHAVIOR -> if (biometrics_layout.findViewById<View>(R.id.typing)
                    .tag == null
            ) ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.typing),
                ColorStateList.valueOf(defaultColor)
            )
            BiometricType.BIOMETRIC_FINGERPRINT -> if (biometrics_layout.findViewById<View>(R.id.fingerprint)
                    .tag == null
            ) ImageViewCompat.setImageTintList(
                biometrics_layout.findViewById<ImageView>(R.id.fingerprint),
                ColorStateList.valueOf(defaultColor)
            )
        }
    }
}