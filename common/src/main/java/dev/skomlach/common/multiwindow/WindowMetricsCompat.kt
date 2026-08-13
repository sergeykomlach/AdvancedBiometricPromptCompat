package dev.skomlach.common.multiwindow

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.window.layout.WindowMetricsCalculator
import dev.skomlach.common.contextprovider.getFixedContext

internal fun buildWindowState(
    activity: Activity?,
    fallbackContext: Context,
    isLegacyMultiWindow: Boolean = false
): WindowState {
    val currentBounds = getCurrentWindowBounds(activity, fallbackContext)
    val maximumBounds = getMaximumWindowBounds(activity, fallbackContext)
    val physicalBounds = getPhysicalDisplayBounds(activity, fallbackContext)
    return calculateWindowState(
        currentBounds = currentBounds,
        maximumBounds = maximumBounds,
        physicalDisplayBounds = physicalBounds,
        safeInsets = getSafeWindowInsets(activity),
        isPlatformMultiWindow = isActivityInMultiWindow(activity),
        isPictureInPicture = isActivityInPictureInPicture(activity),
        isLaunchedFromBubble = isActivityLaunchedFromBubble(activity),
        isLegacyMultiWindow = isLegacyMultiWindow
    )
}

internal fun Rect.toWindowBoundsPx(): WindowBoundsPx {
    return WindowBoundsPx(
        left = left,
        top = top,
        width = width(),
        height = height()
    )
}

private fun isActivityInMultiWindow(activity: Activity?): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        activity?.isInMultiWindowMode == true
    } else {
        false
    }
}

private fun isActivityInPictureInPicture(activity: Activity?): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        activity?.isInPictureInPictureMode == true
    } else {
        false
    }
}

private fun isActivityLaunchedFromBubble(activity: Activity?): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        activity?.isLaunchedFromBubble == true
    } else {
        false
    }
}

private fun getCurrentWindowBounds(activity: Activity?, fallbackContext: Context): WindowBoundsPx {
    activity?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return it.windowManager.currentWindowMetrics.bounds.toWindowBoundsPx()
            } catch (ignore: Throwable) {
            }
        }
        try {
            return WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(it)
                .bounds
                .toWindowBoundsPx()
        } catch (ignore: Throwable) {
        }
    }
    return getDisplayRectBounds(fallbackContext)
}

private fun getMaximumWindowBounds(activity: Activity?, fallbackContext: Context): WindowBoundsPx {
    activity?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return it.windowManager.maximumWindowMetrics.bounds.toWindowBoundsPx()
            } catch (ignore: Throwable) {
            }
        }
        try {
            return WindowMetricsCalculator.getOrCreate()
                .computeMaximumWindowMetrics(it)
                .bounds
                .toWindowBoundsPx()
        } catch (ignore: Throwable) {
        }
    }
    return getDisplayRectBounds(fallbackContext)
}

private fun getPhysicalDisplayBounds(activity: Activity?, fallbackContext: Context): WindowBoundsPx {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val context = activity ?: fallbackContext
        try {
            val display = activity?.display ?: context.display
            display.mode.let { mode ->
                val rotation = display.rotation
                val nativeWidth = mode.physicalWidth
                val nativeHeight = mode.physicalHeight
                val point = when (rotation) {
                    Surface.ROTATION_90, Surface.ROTATION_270 -> Point(nativeHeight, nativeWidth)
                    else -> Point(nativeWidth, nativeHeight)
                }
                return WindowBoundsPx(left = 0, top = 0, width = point.x, height = point.y)
            }
        } catch (ignore: Throwable) {
        }
    }

    @Suppress("DEPRECATION")
    return try {
        val windowManager =
            fallbackContext.getFixedContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        windowManager.defaultDisplay.getRealSize(point)
        WindowBoundsPx(left = 0, top = 0, width = point.x, height = point.y)
    } catch (ignore: Throwable) {
        val metrics = fallbackContext.resources.displayMetrics
        WindowBoundsPx(left = 0, top = 0, width = metrics.widthPixels, height = metrics.heightPixels)
    }
}

private fun getDisplayRectBounds(context: Context): WindowBoundsPx {
    val bounds = Rect()
    @Suppress("DEPRECATION")
    try {
        val windowManager = context.getFixedContext()
            .getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            windowManager.defaultDisplay
        }
        display.getRectSize(bounds)
    } catch (ignore: Throwable) {
    }
    if (!bounds.isEmpty) {
        return bounds.toWindowBoundsPx()
    }
    val metrics = context.resources.displayMetrics
    return WindowBoundsPx(left = 0, top = 0, width = metrics.widthPixels, height = metrics.heightPixels)
}

private fun getSafeWindowInsets(activity: Activity?): WindowInsetsPx {
    val decorView = activity?.window?.decorView ?: return WindowInsetsPx.EMPTY
    return getSafeWindowInsets(decorView)
}

private fun getSafeWindowInsets(view: View): WindowInsetsPx {
    val insets = ViewCompat.getRootWindowInsets(view)
        ?.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
        )
        ?: return WindowInsetsPx.EMPTY
    return WindowInsetsPx(
        left = insets.left,
        top = insets.top,
        right = insets.right,
        bottom = insets.bottom
    )
}
