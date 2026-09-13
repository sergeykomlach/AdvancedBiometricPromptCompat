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
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.palette.graphics.Palette
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricProviderType
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
    private val paletteResults = BackdropPaletteResults()
    private var hasBackdropColor = false

    @Volatile
    private var isBlurViewAttachedToHost = false
    private var biometricsLayout: View? = null
    private var defaultColor = Color.TRANSPARENT
    private val lifecycleEventObserver = object :
        LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_DESTROY) {
                e("${this.javaClass.name}.onStateChanged - ON_DESTROY")
                forceToCloseCallback.onCloseBiometric()
            }
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
                updateBiometricIconsLayout()
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
        parentView.postDelayed(captureRunnable, delay)
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
        ExecutorHelper.postDelayed(timeout, BLUR_CAPTURE_TIMEOUT_MS)
        BiometricLoggerImpl.d("${this.javaClass.name}.updateBackground")
        try {
            BlurUtil.takeScreenshotAndBlur(captureTarget) { originalBitmap, blurredBitmap ->
                ExecutorHelper.removeCallbacks(timeout)
                if (blurCaptureLatch.finish(captureToken, SystemClock.uptimeMillis()) && isBlurViewAttachedToHost) {
                    val currentBitmap = (v?.background as? BitmapDrawable)?.bitmap
                    val unchanged = blurredBitmap != null && currentBitmap != null &&
                        !currentBitmap.isRecycled && blurredBitmap.sameAs(currentBitmap)
                    if (unchanged) {
                        if (blurredBitmap !== currentBitmap) blurredBitmap.recycle()
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
                scheduleBackgroundUpdate()
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
            captureTarget.doOnLayout { captureBackdropPalette() }
            return
        }
        val captureToken = paletteCaptureLatch.tryStart() ?: return
        try {
            BlurUtil.takeScreenshot(captureTarget) { originalBitmap ->
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
        try {
            v?.apply {
                parentView.addView(this)
                post { updateBiometricIconsLayout() }
            }


            if (shouldCaptureBlurBitmap(Utils.isAtLeastS)) {
                requestBackgroundUpdate()
            } else {
                applyRenderEffect()
                v?.post { captureBackdropPalette() }
            }
            IconStateHelper.registerListener(this)
            parentView.doOnAttach {
                parentView.findViewTreeLifecycleOwner()?.lifecycle?.addObserver(
                    lifecycleEventObserver
                )
            }
            if (shouldCaptureBlurBitmap(Utils.isAtLeastS)) {
                parentView.viewTreeObserver.addOnPreDrawListener(onDrawListener)
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        BiometricLoggerImpl.d("${this.javaClass.name}.setupListeners")

    }

    fun resetListeners() {
        val wasAttached = isBlurViewAttachedToHost
        isBlurViewAttachedToHost = false
        blurCaptureLatch.reset()
        captureRequested = false
        captureScheduled = false
        parentView.removeCallbacks(captureRunnable)
        paletteCaptureLatch.reset()
        paletteResults.reset()
        hasBackdropColor = false
        if (wasAttached) {
            try {
                parentView.viewTreeObserver.removeOnPreDrawListener(onDrawListener)
                parentView.findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(
                    lifecycleEventObserver
                )
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
        runBlurCleanup(
            clearRenderEffect = {
                if (Utils.isAtLeastS) {
                    contentView?.setRenderEffect(null)
                }
            },
            removeOverlay = {
                v?.let {
                    parentView.removeView(it)
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
        IconStateHelper.unregisterListener(this)
        BiometricLoggerImpl.d("${this.javaClass.name}.resetListeners")

    }

    private fun updateBiometricIconsLayout() {
        BiometricLoggerImpl.d("${this.javaClass.name}.updateBiometricIconsLayout")
        try {
            biometricsLayout?.let { bmLayout ->
                val list = this.biometricTypesList

                if (list.isEmpty()) {
                    bmLayout.visibility = View.GONE
                } else {
                    bmLayout.visibility = View.VISIBLE
                }
                bmLayout.findViewById<View>(R.id.face)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_FACE)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }
                bmLayout.findViewById<View>(R.id.iris)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_IRIS)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }
                bmLayout.findViewById<View>(R.id.fingerprint)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_FINGERPRINT)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }
                bmLayout.findViewById<View>(R.id.heartrate)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_HEARTRATE)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }
                bmLayout.findViewById<View>(R.id.voice)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_VOICE)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }
                bmLayout.findViewById<View>(R.id.palm)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_PALMPRINT)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }
                bmLayout.findViewById<View>(R.id.typing)?.apply {
                    visibility =
                        if (list.contains(BiometricType.BIOMETRIC_BEHAVIOR)) View.VISIBLE else View.GONE
                    if (tag == null)
                        tag = IconStates.WAITING
                }

                updateIcons()
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    private fun updateDefaultColor(bm: Bitmap) {
        BiometricLoggerImpl.d("${this.javaClass.name}.updateDefaultColor")
        val request = paletteResults.nextRequest()
        var paletteBitmap: Bitmap? = null
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
            val newBm = Bitmap.createBitmap(
                bm,
                crop.left,
                crop.top,
                crop.width,
                crop.height
            )
            paletteBitmap = newBm
            BiometricLoggerImpl.d("${this.javaClass.name}.updateDefaultColor $crop")
            if (newBm !== bm && !bm.isRecycled) {
                bm.recycle()
            }
            // Keep near-white/black backgrounds instead of Palette's default artwork filtering.
            Palette.from(newBm).clearFilters().clearTargets().generate { palette ->
                try {
                    if (!isBlurViewAttachedToHost || !paletteResults.accept(request)) return@generate
                    val paletteDefColor =
                        palette?.getDominantColor(Color.TRANSPARENT)?.also { color ->
                            e(
                                "${this.javaClass.name}.updateDefaultColor#0 isDark - ${
                                    ColorUtil.isDark(
                                        color
                                    )
                                }; color - ${
                                    Integer.toHexString(
                                        color
                                    )
                                }"
                            )
                        } ?: Color.TRANSPARENT

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
                } finally {
                    if (!newBm.isRecycled) newBm.recycle()
                }
            }

        } catch (e: Throwable) {
            paletteBitmap?.takeUnless { it.isRecycled }?.recycle()
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
        BiometricLoggerImpl.d("${this.javaClass.name}.updateIcons")
        try {
            biometricsLayout?.let { bmLayout ->

                for (type in BiometricType.entries) {
                    when (type) {
                        BiometricType.BIOMETRIC_FACE -> setIconState(
                            type,
                            bmLayout.findViewById<View>(R.id.face)?.tag as IconStates?
                        )

                        BiometricType.BIOMETRIC_IRIS -> setIconState(
                            type,
                            bmLayout.findViewById<View>(R.id.iris)?.tag as IconStates?
                        )

                        BiometricType.BIOMETRIC_HEARTRATE -> setIconState(
                            type,
                            bmLayout.findViewById<View>(R.id.heartrate)?.tag as IconStates?
                        )

                        BiometricType.BIOMETRIC_VOICE -> setIconState(
                            type,
                            bmLayout.findViewById<View>(R.id.voice)?.tag as IconStates?
                        )

                        BiometricType.BIOMETRIC_PALMPRINT -> setIconState(
                            type,
                            bmLayout.findViewById<View>(R.id.palm)?.tag as IconStates?
                        )

                        BiometricType.BIOMETRIC_BEHAVIOR -> setIconState(
                            type,
                            bmLayout.findViewById<View>(R.id.typing)?.tag as IconStates?
                        )

                        BiometricType.BIOMETRIC_FINGERPRINT -> setIconState(
                            type,
                            bmLayout.findViewById<View>(R.id.fingerprint)?.tag as IconStates?
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
        biometricsLayout?.post {
            updateBiometricIconsLayout()
            setIconState(type, IconStates.ERROR)
        }
    }

    override fun onSuccess(type: BiometricType?) {
        biometricsLayout?.post {
            updateBiometricIconsLayout()
            setIconState(type, IconStates.SUCCESS)
        }
    }

    override fun reset(type: BiometricType?) {
        biometricsLayout?.post {
            updateBiometricIconsLayout()
            setIconState(type, IconStates.WAITING)
        }
    }

    private fun setIconState(type: BiometricType?, iconStates: IconStates?) {
        BiometricLoggerImpl.d("${this.javaClass.name}.setIconState $type=$iconStates")
        try {
            biometricsLayout?.let { bmLayout ->
                val color = if (iconStates == null) defaultColor else when (iconStates) {
                    IconStates.WAITING -> defaultColor
                    IconStates.ERROR -> Color.RED
                    IconStates.SUCCESS -> Color.GREEN
                }
                bmLayout.findViewById<View>(R.id.biometric_divider)
                    .setBackgroundColor(defaultColor)

                when (type) {
                    BiometricType.BIOMETRIC_FACE -> {
                        bmLayout.findViewById<View>(R.id.face)?.tag = iconStates
                        bmLayout.findViewById<ImageView>(R.id.face).setColorFilter(color)
                    }

                    BiometricType.BIOMETRIC_IRIS -> {
                        bmLayout.findViewById<View>(R.id.iris)?.tag = iconStates
                        bmLayout.findViewById<ImageView>(R.id.iris).setColorFilter(color)
                    }

                    BiometricType.BIOMETRIC_HEARTRATE -> {
                        bmLayout.findViewById<View>(R.id.heartrate)?.tag = iconStates
                        bmLayout.findViewById<ImageView>(R.id.heartrate)
                            .setColorFilter(color)
                    }

                    BiometricType.BIOMETRIC_VOICE -> {
                        bmLayout.findViewById<View>(R.id.voice)?.tag = iconStates
                        bmLayout.findViewById<ImageView>(R.id.voice).setColorFilter(color)
                    }

                    BiometricType.BIOMETRIC_PALMPRINT -> {
                        bmLayout.findViewById<View>(R.id.palm)?.tag = iconStates
                        bmLayout.findViewById<ImageView>(R.id.palm).setColorFilter(color)
                    }

                    BiometricType.BIOMETRIC_BEHAVIOR -> {
                        bmLayout.findViewById<View>(R.id.typing)?.tag = iconStates
                        bmLayout.findViewById<ImageView>(R.id.typing).setColorFilter(color)
                    }

                    BiometricType.BIOMETRIC_FINGERPRINT -> {
                        bmLayout.findViewById<View>(R.id.fingerprint)?.tag = iconStates
                        bmLayout.findViewById<ImageView>(R.id.fingerprint)
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
