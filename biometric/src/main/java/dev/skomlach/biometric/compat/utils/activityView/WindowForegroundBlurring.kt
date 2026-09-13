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
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.DialogMainColor
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.blur.BlurUtil
import dev.skomlach.common.blur.DEFAULT_RADIUS
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.statusbar.ColorUtil

class WindowForegroundBlurring(
    private val compatBuilder: BiometricPromptCompat.Builder,
    private val parentView: ViewGroup,
    private val forceToCloseCallback: ActivityViewWatcher.ForceToCloseCallback
) : IconStateHelper.IconStateListener {
    private val context = compatBuilder.getContext()
    private var contentView: ViewGroup? = null
    private var v: View? = null
    private var renderEffect: RenderEffect? = null
    private val blurCaptureLatch = BlurCaptureLatch(minCaptureIntervalMillis = 50L)
    private val paletteCaptureLatch = BlurCaptureLatch()
    private var captureRequested = false
    private var captureScheduled = false
    private val captureRunnable = Runnable {
        captureScheduled = false
        updateBackground()
    }
    private val captureFrameCallback = Runnable {
        // Run capture after this frame's traversal, giving the dialog a chance to draw first.
        if (isBlurViewAttachedToHost && captureScheduled) parentView.post(captureRunnable)
    }
    private val paletteResults = BackdropPaletteResults()
    private var hasBackdropColor = false

    @Volatile
    private var isBlurViewAttachedToHost = false
    private var biometricsLayout: View? = null
    private var defaultColor = Color.TRANSPARENT
    private var blurSession: BlurUtil.BlurSession? = null
    private var captureTimeout: Runnable? = null
    private var paletteLayoutListener: View.OnLayoutChangeListener? = null
    private var paletteTask: BackdropPaletteTask? = null
    private val iconViews = linkedMapOf<BiometricType, ImageView>()
    private val iconColors = mutableMapOf<BiometricType, Int>()
    private var dividerColor: Int? = null
    private val updateIconsRunnable = Runnable { if (isBlurViewAttachedToHost) updateBiometricIconsLayout() }
    private val paletteRunnable = Runnable { captureBackdropPalette() }
    private val hostListeners by lazy {
        BlurHostListeners(parentView, if (shouldCaptureBlurBitmap(Utils.isAtLeastS)) onDrawListener else null) {
            forceToCloseCallback.onCloseBiometric()
        }
    }

    private val biometricTypesList: List<BiometricType>
        get() {
            // System prompt ownership suppresses the compat dialog, not these status icons.
            // RenderEffect belongs to contentView; this sibling overlay stays sharp.
            return if (!isBlurViewAttachedToHost) {
                emptyList()
            } else {
                val typesList = if (compatBuilder.isBackgroundBiometricIconsEnabled()) {
                    if (compatBuilder.enroll) {
                        compatBuilder.getEffectiveAvailableTypes().toList()
                    } else {
                        compatBuilder.getAllAvailableTypes().filter {
                            val list = compatBuilder.getSelectedTypePermissions(it)
                            list.isEmpty() || PermissionUtils.INSTANCE.hasSelfPermissions(list)
                        }
                    }
                } else emptyList()
                typesList.filter {
                    if (compatBuilder.enroll) {
                        return@filter true
                    }
                    compatBuilder.isSelectedTypeAvailable(it, ignoreCameraCheck = false)
                }
            }
        }
    private val onDrawListener = ViewTreeObserver.OnPreDrawListener {
        requestBackgroundUpdate()
        true
    }

    init {
        val isDark = DarkLightThemes.isNightMode(compatBuilder.getContext())
        defaultColor = DialogMainColor.getColor(context, !isDark)
        e(
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

        @SuppressLint("ClickableViewAccessibility")
        v = LayoutInflater.from(parentView.context)
            .inflate(R.layout.blurred_screen, null, false).apply {
                tag = this@WindowForegroundBlurring.javaClass.name
                alpha = 1f
                biometricsLayout = findViewById(R.id.biometrics_layout)
                mapOf(
                    BiometricType.BIOMETRIC_FACE to R.id.face,
                    BiometricType.BIOMETRIC_IRIS to R.id.iris,
                    BiometricType.BIOMETRIC_FINGERPRINT to R.id.fingerprint,
                    BiometricType.BIOMETRIC_HEARTRATE to R.id.heartrate,
                    BiometricType.BIOMETRIC_VOICE to R.id.voice,
                    BiometricType.BIOMETRIC_PALMPRINT to R.id.palm,
                    BiometricType.BIOMETRIC_BEHAVIOR to R.id.typing
                ).forEach { (type, id) ->
                    findViewById<ImageView>(id)?.let { iconViews[type] = it }
                }
                biometricsLayout?.visibility = View.GONE
                isFocusable = true
                isClickable = true
                isLongClickable = true
                setOnTouchListener { _, _ ->
                    true
                }
                if (!Utils.isAtLeastS)
                    ViewCompat.setBackground(this, Color.TRANSPARENT.toDrawable())
            }

    }

    private fun requestBackgroundUpdate() {
        captureRequested = true
        scheduleBackgroundUpdate()
    }

    private fun scheduleBackgroundUpdate() {
        if (!isBlurViewAttachedToHost || !captureRequested || captureScheduled) return
        val delay = blurCaptureLatch.delayUntilReady(SystemClock.uptimeMillis()) ?: return
        captureScheduled = true
        parentView.postOnAnimationDelayed(captureFrameCallback, delay)
    }

    private fun updateBackground() {
        if (!isBlurViewAttachedToHost)
            return
        if (!shouldCaptureBlurBitmap(Utils.isAtLeastS)) {
            applyRenderEffect()
            return
        }
        val captureTarget = contentView ?: return
        if (captureTarget.width <= 0 || captureTarget.height <= 0) return
        val captureToken = blurCaptureLatch.tryStart(SystemClock.uptimeMillis()) ?: return
        captureRequested = false
        val timeout = Runnable {
            if (blurCaptureLatch.finish(captureToken, SystemClock.uptimeMillis())) scheduleBackgroundUpdate()
        }
        captureTimeout = timeout
        ExecutorHelper.postDelayed(timeout, BLUR_CAPTURE_TIMEOUT_MS)
        BiometricLoggerImpl.d("${this.javaClass.name}.updateBackground")
        try {
            blurSession?.takeScreenshotAndBlur(captureTarget) { originalBitmap, blurredBitmap ->
                if (!blurCaptureLatch.owns(captureToken) || !isBlurViewAttachedToHost) {
                    ExecutorHelper.removeCallbacks(timeout)
                    recycleUnusedCapture(originalBitmap, blurredBitmap)
                    return@takeScreenshotAndBlur
                }
                val currentBitmap = (v?.background as? BitmapDrawable)?.bitmap
                fun publishResult(matchesPrevious: Boolean) {
                    ExecutorHelper.removeCallbacks(timeout)
                    try {
                        if (blurCaptureLatch.finish(captureToken, SystemClock.uptimeMillis()) && isBlurViewAttachedToHost) {
                            val unchanged = matchesPrevious &&
                                    (v?.background as? BitmapDrawable)?.bitmap === currentBitmap
                            if (unchanged) {
                                if (blurredBitmap !== currentBitmap) blurredBitmap?.recycle()
                            } else if (blurredBitmap != null) {
                                setDrawable(blurredBitmap)
                            }
                            if (blurredBitmap != null && (!unchanged || !hasBackdropColor)) {
                                updateDefaultColor(originalBitmap)
                            } else if (!originalBitmap.isRecycled) {
                                originalBitmap.recycle()
                            }
                        } else {
                            recycleUnusedCapture(originalBitmap, blurredBitmap)
                        }
                    } finally {
                        scheduleBackgroundUpdate()
                    }
                }
                if (blurredBitmap == null || currentBitmap == null) {
                    publishResult(false)
                } else {
                    // Published bitmaps stay owned by their drawable; do not recycle them while
                    // a worker holds a reference. Unpublished captures are released after comparison.
                    ExecutorHelper.startOnBackground {
                        val unchanged = try {
                            !currentBitmap.isRecycled && blurredBitmap.sameAs(currentBitmap)
                        } catch (error: Throwable) {
                            BiometricLoggerImpl.e(error)
                            false
                        }
                        ExecutorHelper.post { publishResult(unchanged) }
                    }
                }
            }
        } catch (e: Throwable) {
            ExecutorHelper.removeCallbacks(timeout)
            blurCaptureLatch.finish(captureToken, SystemClock.uptimeMillis())
            scheduleBackgroundUpdate()
            BiometricLoggerImpl.e(e)
        }
    }

    private fun setDrawable(bm: Bitmap?) {
        BiometricLoggerImpl.d("${this.javaClass.name}.setDrawable")
        try {
            v?.let {
                if (Utils.isAtLeastS) {
                    applyRenderEffect()
                } else
                    ViewCompat.setBackground(it, bm?.toDrawable(it.resources))
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    private fun applyRenderEffect() {
        if (!Utils.isAtLeastS || !isBlurViewAttachedToHost) {
            return
        }
        if (renderEffect == null) {
            renderEffect = RenderEffect.createBlurEffect(
                DEFAULT_RADIUS.toFloat(),
                DEFAULT_RADIUS.toFloat(),
                Shader.TileMode.DECAL
            )
        }
        contentView?.setRenderEffect(renderEffect)
    }

    private fun captureBackdropPalette() {
        if (!isBlurViewAttachedToHost ||
            !shouldCaptureBackdropPalette(Utils.isAtLeastS)
        ) {
            return
        }
        val captureTarget = contentView ?: return
        if (captureTarget.width <= 0 || captureTarget.height <= 0) {
            if (paletteLayoutListener == null) {
                paletteLayoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    paletteLayoutListener?.let(view::removeOnLayoutChangeListener)
                    paletteLayoutListener = null
                    captureBackdropPalette()
                }.also(captureTarget::addOnLayoutChangeListener)
            }
            return
        }
        val captureToken = paletteCaptureLatch.tryStart() ?: return
        try {
            blurSession?.takeScreenshot(captureTarget) { originalBitmap ->
                if (paletteCaptureLatch.finish(captureToken) && isBlurViewAttachedToHost) {
                    updateDefaultColor(originalBitmap)
                } else if (!originalBitmap.isRecycled) {
                    originalBitmap.recycle()
                }
            }
        } catch (error: Throwable) {
            paletteCaptureLatch.finish(captureToken)
            BiometricLoggerImpl.e(error)
        }
    }

    fun setupListeners() {
        if (isBlurViewAttachedToHost) return
        isBlurViewAttachedToHost = true
        blurSession = BlurUtil.BlurSession()
        iconViews.values.forEach { it.tag = IconStates.WAITING }
        try {
            v?.apply {
                parentView.addView(this)
                post(updateIconsRunnable)
            }


            if (shouldCaptureBlurBitmap(Utils.isAtLeastS)) {
                requestBackgroundUpdate()
            } else {
                applyRenderEffect()
                v?.post(paletteRunnable)
            }
            IconStateHelper.registerListener(this)
            hostListeners.start()
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        BiometricLoggerImpl.d("${this.javaClass.name}.setupListeners")

    }

    fun resetListeners() {
        isBlurViewAttachedToHost = false
        blurCaptureLatch.reset()
        captureRequested = false
        captureScheduled = false
        parentView.removeCallbacks(captureRunnable)
        parentView.removeCallbacks(captureFrameCallback)
        paletteCaptureLatch.reset()
        paletteResults.reset()
        paletteTask?.cancel()
        paletteTask = null
        hasBackdropColor = false
        captureTimeout?.let(ExecutorHelper::removeCallbacks)
        captureTimeout = null
        v?.removeCallbacks(updateIconsRunnable)
        v?.removeCallbacks(paletteRunnable)
        paletteLayoutListener?.let { contentView?.removeOnLayoutChangeListener(it) }
        paletteLayoutListener = null
        IconStateHelper.unregisterListener(this)
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
                parentView.findViewWithTag<View?>(this@WindowForegroundBlurring.javaClass.name)
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

    private fun updateBiometricIconsLayout() {
        if (!isBlurViewAttachedToHost) return
        val visibleTypes = biometricTypesList.toSet()
        biometricsLayout?.visibility = if (visibleTypes.isEmpty()) View.GONE else View.VISIBLE
        iconViews.forEach { (type, icon) ->
            icon.visibility = if (type in visibleTypes) View.VISIBLE else View.GONE
            if (icon.tag == null) icon.tag = IconStates.WAITING
        }
        updateIcons()
    }

    private fun updateDefaultColor(bm: Bitmap) {
        BiometricLoggerImpl.d("${this.javaClass.name}.updateDefaultColor")
        val request = paletteResults.nextRequest()
        try {
            val biometricsRect = Rect()
            biometricsLayout?.findViewById<View>(R.id.biometrics)?.getGlobalVisibleRect(biometricsRect)
            val contentRect = Rect()
            contentView?.getGlobalVisibleRect(contentRect)

            if (biometricsRect.isEmpty || contentRect.isEmpty) {
                if (!bm.isRecycled) bm.recycle()
                return
            }
            val crop = resolveBitmapCropBounds(
                targetScreenLeft = biometricsRect.left,
                targetScreenTop = biometricsRect.top,
                targetWidth = biometricsRect.width(),
                targetHeight = biometricsRect.height(),
                bitmapHostScreenLeft = contentRect.left,
                bitmapHostScreenTop = contentRect.top,
                bitmapWidth = bm.width,
                bitmapHeight = bm.height
            ) ?: run {
                if (!bm.isRecycled) bm.recycle()
                return
            }
            paletteTask?.cancel()
            paletteTask = BackdropPaletteTask(bm, crop) { paletteDefColor ->
                if (!isBlurViewAttachedToHost || !paletteResults.accept(request)) return@BackdropPaletteTask
                paletteTask = null
                try {
                    val previousColor = defaultColor
                    hasBackdropColor = paletteDefColor != Color.TRANSPARENT
                    defaultColor = if (hasBackdropColor) {
                        val lightColor = DialogMainColor.getColor(context, false)
                        val darkColor = DialogMainColor.getColor(context, true)
                        if (shouldUseDarkBackdropIcons(
                                backgroundLuminance = ColorUtils.calculateLuminance(paletteDefColor),
                                lightIconLuminance = ColorUtils.calculateLuminance(lightColor),
                                darkIconLuminance = ColorUtils.calculateLuminance(darkColor)
                            )) darkColor else lightColor
                    } else {
                        val isDark = DarkLightThemes.isNightMode(compatBuilder.getContext())
                        DialogMainColor.getColor(context, !isDark)
                    }

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
                    if (defaultColor != previousColor) updateIcons()
                } catch (e: Throwable) {
                    BiometricLoggerImpl.e(e)
                }
            }.also { it.start() }
        } catch (e: Throwable) {
            if (!bm.isRecycled) bm.recycle()
            BiometricLoggerImpl.e(e)
        }
    }

    private fun recycleUnusedCapture(originalBitmap: Bitmap, blurredBitmap: Bitmap?) {
        if (!originalBitmap.isRecycled) originalBitmap.recycle()
        if (blurredBitmap !== originalBitmap && blurredBitmap?.isRecycled == false) {
            blurredBitmap.recycle()
        }
    }

    private fun updateIcons() {
        if (!isBlurViewAttachedToHost) return
        if (dividerColor != defaultColor) {
            biometricsLayout?.findViewById<View>(R.id.biometric_divider)?.setBackgroundColor(defaultColor)
            dividerColor = defaultColor
        }
        iconViews.forEach { (type, icon) -> setIconState(type, icon.tag as? IconStates) }
    }

    // IconStateHelper dispatches to main. Ordinary state changes do not probe hardware again.
    override fun onError(type: BiometricType?) = setIconState(type, IconStates.ERROR)
    override fun onSuccess(type: BiometricType?) = setIconState(type, IconStates.SUCCESS)
    override fun reset(type: BiometricType?) = setIconState(type, IconStates.WAITING)
    override fun onAvailabilityChanged() = updateBiometricIconsLayout()

    private fun setIconState(type: BiometricType?, iconStates: IconStates?) {
        if (!isBlurViewAttachedToHost) return
        val icon = iconViews[type] ?: return
        icon.tag = iconStates
        val color = when (iconStates) {
            IconStates.ERROR -> Color.RED
            IconStates.SUCCESS -> Color.GREEN
            else -> defaultColor
        }
        if (iconColors[type] != color) {
            icon.setColorFilter(color)
            iconColors[type!!] = color
        }
    }

    enum class IconStates {
        WAITING,
        ERROR,
        SUCCESS
    }

    private companion object {
        const val BLUR_CAPTURE_TIMEOUT_MS = 2_000L
    }
}

internal data class BitmapCropBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

internal fun resolveBitmapCropBounds(
    targetScreenLeft: Int,
    targetScreenTop: Int,
    targetWidth: Int,
    targetHeight: Int,
    bitmapHostScreenLeft: Int,
    bitmapHostScreenTop: Int,
    bitmapWidth: Int,
    bitmapHeight: Int
): BitmapCropBounds? {
    if (targetWidth <= 0 || targetHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
        return null
    }
    val localLeft = targetScreenLeft - bitmapHostScreenLeft
    val localTop = targetScreenTop - bitmapHostScreenTop
    val left = localLeft.coerceIn(0, bitmapWidth)
    val top = localTop.coerceIn(0, bitmapHeight)
    val right = (localLeft + targetWidth).coerceIn(0, bitmapWidth)
    val bottom = (localTop + targetHeight).coerceIn(0, bitmapHeight)
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) {
        return null
    }
    return BitmapCropBounds(left = left, top = top, width = width, height = height)
}
