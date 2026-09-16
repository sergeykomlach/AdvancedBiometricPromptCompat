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
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.blur.BlurUtil
import dev.skomlach.common.blur.DEFAULT_RADIUS
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils

class WindowBackgroundBlurring(
    private val parentView: ViewGroup
) {
    private var contentView: View? = null
    private var v: View? = null
    private var renderEffect: RenderEffect? = null
    private var isBlurViewAttachedToHost = false
    private val captureLatch = BlurCaptureLatch(minCaptureIntervalMillis = 50L)
    private var captureRequested = false
    private var captureScheduled = false
    private val captureRunnable = Runnable {
        captureScheduled = false
        updateBackground()
    }
    private var biometricsLayout: View? = null
    private var blurSession: BlurUtil.BlurSession? = null
    private var captureTimeout: Runnable? = null
    private val hostListeners by lazy {
        BlurHostListeners(parentView, if (shouldCaptureBlurBitmap(Utils.isAtLeastS)) onDrawListener else null) {
            resetListeners()
        }
    }
    private val captureFrameCallback = Runnable {
        if (isBlurViewAttachedToHost && captureScheduled) parentView.post(captureRunnable)
    }
    private val onDrawListener = ViewTreeObserver.OnPreDrawListener {
        requestBackgroundUpdate()
        true
    }

    init {
        for (i in 0 until parentView.childCount) {
            val v = parentView.getChildAt(i)
            // A popup can contain a plain View rather than a nested ViewGroup.
            if (v is ViewGroup || contentView == null) {
                contentView = v
            }
        }
    }

    private fun requestBackgroundUpdate() {
        captureRequested = true
        scheduleBackgroundUpdate()
    }

    private fun scheduleBackgroundUpdate() {
        if (!isBlurViewAttachedToHost || !captureRequested || captureScheduled) return
        val delay = captureLatch.delayUntilReady(SystemClock.uptimeMillis()) ?: return
        captureScheduled = true
        parentView.postOnAnimationDelayed(captureFrameCallback, delay)
    }

    private fun updateBackground() {
        if (!isBlurViewAttachedToHost)
            return
        if (!shouldCaptureBlurBitmap(Utils.isAtLeastS)) {
            setDrawable(null)
            return
        }
        val captureTarget = contentView ?: return
        if (captureTarget.width <= 0 || captureTarget.height <= 0) return
        val captureToken = captureLatch.tryStart(SystemClock.uptimeMillis()) ?: return
        captureRequested = false
        val timeout = Runnable {
            if (captureLatch.finish(captureToken, SystemClock.uptimeMillis())) scheduleBackgroundUpdate()
        }
        captureTimeout = timeout
        ExecutorHelper.postDelayed(timeout, BLUR_CAPTURE_TIMEOUT_MS)
        BiometricLoggerImpl.d("${this.javaClass.name}.updateBackground")
        try {
            blurSession?.takeScreenshotAndBlur(captureTarget) { originalBitmap, blurredBitmap ->
                if (!captureLatch.owns(captureToken) || !isBlurViewAttachedToHost) {
                    ExecutorHelper.removeCallbacks(timeout)
                    recycleUnusedCapture(originalBitmap, blurredBitmap)
                    return@takeScreenshotAndBlur
                }
                val currentBitmap = (v?.background as? BitmapDrawable)?.bitmap
                fun publish(matchesPrevious: Boolean) {
                    ExecutorHelper.removeCallbacks(timeout)
                    try {
                        if (captureLatch.finish(captureToken, SystemClock.uptimeMillis()) && isBlurViewAttachedToHost) {
                            val unchanged = matchesPrevious &&
                                    (v?.background as? BitmapDrawable)?.bitmap === currentBitmap
                            if (unchanged) {
                                if (blurredBitmap !== currentBitmap) blurredBitmap?.recycle()
                            } else if (blurredBitmap != null) setDrawable(blurredBitmap)
                            if (originalBitmap !== blurredBitmap) originalBitmap.recycle()
                        } else recycleUnusedCapture(originalBitmap, blurredBitmap)
                    } finally {
                        scheduleBackgroundUpdate()
                    }
                }
                if (currentBitmap == null || blurredBitmap == null) publish(false)
                else ExecutorHelper.startOnBackground {
                    val unchanged = try {
                        !currentBitmap.isRecycled && blurredBitmap.sameAs(currentBitmap)
                    } catch (error: Throwable) {
                        BiometricLoggerImpl.e(error)
                        false
                    }
                    ExecutorHelper.post { publish(unchanged) }
                }
            }
        } catch (e: Throwable) {
            ExecutorHelper.removeCallbacks(timeout)
            captureLatch.finish(captureToken, SystemClock.uptimeMillis())
            scheduleBackgroundUpdate()
            BiometricLoggerImpl.e(e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setDrawable(bm: Bitmap?) {
        if (!isBlurViewAttachedToHost)
            return
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
                    ViewCompat.setBackground(it, bm?.toDrawable(it.resources))
            } ?: run {
                v = LayoutInflater.from(parentView.context)
                    .inflate(R.layout.blurred_screen, null, false).apply {
                        tag = this@WindowBackgroundBlurring.javaClass.name
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
                            ViewCompat.setBackground(this, bm?.toDrawable(this.resources))
                        parentView.addView(this)

                    }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    fun setupListeners() {
        if (isBlurViewAttachedToHost) return
        isBlurViewAttachedToHost = true
        blurSession = BlurUtil.BlurSession()
        try {
            if (shouldCaptureBlurBitmap(Utils.isAtLeastS)) {
                // Reattach before comparing against the bitmap retained from the previous showing.
                v?.takeIf { it.parent == null }?.let { parentView.addView(it) }
                requestBackgroundUpdate()
            } else {
                setDrawable(null)
            }
            hostListeners.start()
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        BiometricLoggerImpl.d("${this.javaClass.name}.setupListeners")

    }

    fun resetListeners() {
        isBlurViewAttachedToHost = false
        captureLatch.reset()
        captureRequested = false
        captureScheduled = false
        parentView.removeCallbacks(captureRunnable)
        parentView.removeCallbacks(captureFrameCallback)
        captureTimeout?.let(ExecutorHelper::removeCallbacks)
        captureTimeout = null
        hostListeners.stop()
        blurSession?.close()
        blurSession = null
        runBlurCleanup(
            clearRenderEffect = {
                if (Utils.isAtLeastS) {
                    contentView?.setRenderEffect(null)
                }
            },
            removeOverlay = {
                v?.let {
                    parentView.removeView(it)
                    it.background = null
                }
                parentView.findViewWithTag<View?>(this@WindowBackgroundBlurring.javaClass.name)
                    ?.let {
                        parentView.removeView(it)
                    }
            },
            invalidateHost = {
                contentView?.invalidate()
                parentView.invalidate()
            },
            onFailure = { BiometricLoggerImpl.e(it) }
        )
        BiometricLoggerImpl.d("${this.javaClass.name}.resetListeners")
    }

    private fun recycleUnusedCapture(original: Bitmap, blurred: Bitmap?) {
        if (!original.isRecycled) original.recycle()
        if (blurred !== original && blurred?.isRecycled == false) blurred.recycle()
    }

    private companion object {
        const val BLUR_CAPTURE_TIMEOUT_MS = 2_000L
    }

}
