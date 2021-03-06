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
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl

class ActivityViewWatcher(
    private val context: FragmentActivity,
    private val forceToCloseCallback: ForceToCloseCallback
) {
    private val parentView: ViewGroup = context.findViewById<ViewGroup>(Window.ID_ANDROID_CONTENT)
    private lateinit var contentView: ViewGroup
    private var v: View? = null
    private var isAttached = false
    private var drawingInProgress = false
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
                v = View(ContextWrapper(context)).apply {
                    tag = tag
                    val lp = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    layoutParams = lp
                    alpha = 1f

                    isFocusable = true
                    isClickable = true
                    isLongClickable = true
                    setOnTouchListener { _, _ ->
                        true
                    }
                    ViewCompat.setBackground(this, BitmapDrawable(this.resources, bm))
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
        try {
            parentView.removeOnAttachStateChangeListener(attachStateChangeListener)
            parentView.viewTreeObserver.removeOnDrawListener(onDrawListener)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        try {
            v?.let {
                parentView.removeView(it)
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    interface ForceToCloseCallback {
        fun onCloseBiometric()
    }
}