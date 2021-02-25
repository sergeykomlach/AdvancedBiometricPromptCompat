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

    private val vg: ViewGroup = context.findViewById<ViewGroup>(Window.ID_ANDROID_CONTENT)
    private val tag = "biometric-" + context.javaClass.name
    private val attachStateChangeListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View?) {
            BiometricLoggerImpl.e("onViewAttachedToWindow")
        }

        override fun onViewDetachedFromWindow(v: View?) {
            BiometricLoggerImpl.e("onViewDetachedFromWindow")
            resetListeners()
            forceToCloseCallback.onCloseBiometric()
        }
    }
    private val onGlobalLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        @SuppressLint("ClickableViewAccessibility")
        private fun updateBg(bm: Bitmap) {
            try {
                var v = vg.findViewWithTag<View?>(tag)
                if (v != null) {
                    ViewCompat.setBackground(v, BitmapDrawable(bm))
                } else {
                    v = View(ContextWrapper(context))
                    v.tag = tag
                    val lp = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    v.layoutParams = lp
                    v.alpha = 1f
                    ViewCompat.setBackground(v, BitmapDrawable(bm))
                    v.isFocusable = true
                    v.isClickable = true
                    v.isLongClickable = true
                    v.setOnTouchListener { _, _ ->
                        true
                    }
                    vg.addView(v)
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }

        override fun onGlobalLayout() {

            try {
                vg.findViewWithTag<View?>(tag)?.let {
                    it.visibility = View.INVISIBLE
                }
                BlurUtil.takeScreenshotAndBlur(
                    vg,
                    object : BlurUtil.OnPublishListener {
                        override fun onBlurredScreenshot(bm: Bitmap) {
                            vg.findViewWithTag<View?>(tag)?.let {
                                it.visibility = View.VISIBLE
                            }
                            updateBg(bm)
                        }
                    })
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
    }

    init {
        vg.addOnAttachStateChangeListener(attachStateChangeListener)
    }

    fun setupListeners() {
        try {
            onGlobalLayoutListener.onGlobalLayout()
            vg.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    fun resetListeners() {

        try {
            vg.removeOnAttachStateChangeListener(attachStateChangeListener)
            vg.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        try {
            val v = vg.findViewWithTag<View?>(tag)
            v?.let {
                vg.removeView(v)
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    interface ForceToCloseCallback {
        fun onCloseBiometric()
    }
}