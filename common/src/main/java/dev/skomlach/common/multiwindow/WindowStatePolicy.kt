package dev.skomlach.common.multiwindow

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import kotlin.math.abs

internal const val MATCH_PARENT_WIDTH = -1

internal data class WindowBoundsPx(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) {
    val right: Int
        get() = left + width
    val bottom: Int
        get() = top + height
    val centerY: Int
        get() = top + (height / 2)
    val isEmpty: Boolean
        get() = width <= 0 || height <= 0
}

internal data class WindowInsetsPx(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    companion object {
        val EMPTY = WindowInsetsPx(left = 0, top = 0, right = 0, bottom = 0)
    }
}

internal enum class WindowOrientation {
    PORTRAIT,
    LANDSCAPE,
    SQUARE
}

internal data class WindowState(
    val currentBounds: WindowBoundsPx,
    val maximumBounds: WindowBoundsPx,
    val physicalDisplayBounds: WindowBoundsPx,
    val safeInsets: WindowInsetsPx,
    val isPlatformMultiWindow: Boolean,
    val isPictureInPicture: Boolean,
    val isLaunchedFromBubble: Boolean,
    val isLegacyMultiWindow: Boolean
) {
    val safeCurrentWindowWidth: Int
        get() = (currentBounds.width - safeInsets.left - safeInsets.right).coerceAtLeast(0)

    val safeCurrentWindowHeight: Int
        get() = (currentBounds.height - safeInsets.top - safeInsets.bottom).coerceAtLeast(0)

    val orientation: WindowOrientation
        get() = orientationOf(currentBounds)

    @get:Suppress("DEPRECATION")
    val configurationOrientation: Int
        get() = when (orientation) {
            WindowOrientation.PORTRAIT -> Configuration.ORIENTATION_PORTRAIT
            WindowOrientation.LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
            WindowOrientation.SQUARE -> Configuration.ORIENTATION_SQUARE
        }

    val requestedOrientation: Int
        get() = when (orientation) {
            WindowOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            WindowOrientation.SQUARE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

    val isCurrentWindowConstrained: Boolean
        get() = isMeaningfullyDifferent(currentBounds, maximumBounds)

    val isInWindowedMode: Boolean
        get() = isPlatformMultiWindow ||
                isPictureInPicture ||
                isLaunchedFromBubble ||
                isLegacyMultiWindow ||
                isCurrentWindowConstrained

    val isWindowOnScreenBottom: Boolean
        get() = isInWindowedMode && currentBounds.centerY > maximumBounds.centerY
}

internal fun calculateWindowState(
    currentBounds: WindowBoundsPx,
    maximumBounds: WindowBoundsPx,
    physicalDisplayBounds: WindowBoundsPx,
    safeInsets: WindowInsetsPx,
    isPlatformMultiWindow: Boolean,
    isPictureInPicture: Boolean,
    isLaunchedFromBubble: Boolean,
    isLegacyMultiWindow: Boolean
): WindowState {
    val maximum = maximumBounds.takeUnless { it.isEmpty }
        ?: physicalDisplayBounds.takeUnless { it.isEmpty }
        ?: currentBounds
    val current = currentBounds.takeUnless { it.isEmpty } ?: maximum
    val physical = physicalDisplayBounds.takeUnless { it.isEmpty } ?: maximum
    return WindowState(
        currentBounds = current,
        maximumBounds = maximum,
        physicalDisplayBounds = physical,
        safeInsets = safeInsets,
        isPlatformMultiWindow = isPlatformMultiWindow,
        isPictureInPicture = isPictureInPicture,
        isLaunchedFromBubble = isLaunchedFromBubble,
        isLegacyMultiWindow = isLegacyMultiWindow
    )
}

internal fun resolveDialogWidthPx(
    configuredWidthPx: Int,
    currentBounds: WindowBoundsPx,
    safeInsets: WindowInsetsPx
): Int {
    if (configuredWidthPx <= 0) {
        return MATCH_PARENT_WIDTH
    }
    val safeWidth = (currentBounds.width - safeInsets.left - safeInsets.right).coerceAtLeast(0)
    val availableWidth = safeWidth.takeIf { it > 0 } ?: currentBounds.width
    return configuredWidthPx.coerceAtMost(availableWidth)
}

private fun orientationOf(bounds: WindowBoundsPx): WindowOrientation {
    val width = bounds.width.coerceAtLeast(1)
    val height = bounds.height.coerceAtLeast(1)
    val max = width.coerceAtLeast(height).toDouble()
    val min = width.coerceAtMost(height).toDouble()
    if (max / min <= 1.25) {
        return WindowOrientation.SQUARE
    }
    return if (width < height) {
        WindowOrientation.PORTRAIT
    } else {
        WindowOrientation.LANDSCAPE
    }
}

private fun isMeaningfullyDifferent(current: WindowBoundsPx, maximum: WindowBoundsPx): Boolean {
    if (current.isEmpty || maximum.isEmpty) {
        return false
    }
    val threshold = (maximum.width.coerceAtMost(maximum.height) * 0.03f).toInt()
        .coerceIn(24, 96)
    return abs(current.left - maximum.left) > threshold ||
            abs(current.top - maximum.top) > threshold ||
            maximum.width - current.width > threshold ||
            maximum.height - current.height > threshold
}
