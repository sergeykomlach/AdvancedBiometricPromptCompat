package dev.skomlach.biometric.compat.utils.activityView

import android.view.View
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

/** Removes registrations from the exact owners used when attaching, including deferred attach. */
internal class BlurHostListeners(
    private val host: View,
    private val preDraw: ViewTreeObserver.OnPreDrawListener?,
    private val onHostClosed: () -> Unit
) : View.OnAttachStateChangeListener {
    private var active = false
    private var lifecycle: Lifecycle? = null
    private var tree: ViewTreeObserver? = null
    private val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY && active) {
            stop()
            onHostClosed()
        }
    }

    fun start() {
        if (active) return
        active = true
        host.addOnAttachStateChangeListener(this)
        if (ViewCompat.isAttachedToWindow(host)) onViewAttachedToWindow(host)
    }

    override fun onViewAttachedToWindow(v: View) {
        if (!active) return
        lifecycle = host.findViewTreeLifecycleOwner()?.lifecycle
        lifecycle?.addObserver(observer)
        if (active && preDraw != null) {
            tree = host.viewTreeObserver
            tree?.addOnPreDrawListener(preDraw)
        }
    }

    override fun onViewDetachedFromWindow(v: View) {
        if (!active) return
        stop()
        onHostClosed()
    }

    fun stop() {
        active = false
        host.removeOnAttachStateChangeListener(this)
        lifecycle?.removeObserver(observer)
        lifecycle = null
        preDraw?.let { tree?.takeIf { it.isAlive }?.removeOnPreDrawListener(it) }
        tree = null
    }
}
