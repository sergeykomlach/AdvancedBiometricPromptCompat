package dev.skomlach.biometric.compat.utils.activityView

internal data class FeedbackBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    fun relativeTo(x: Int, y: Int) = FeedbackBounds(left - x, top - y, right - x, bottom - y)
}

/** Null means there is no room above a known panel; do not silently draw on top of it. */
internal fun feedbackGroupTop(viewport: FeedbackBounds, dialog: FeedbackBounds?, height: Int, gap: Int): Int? {
    if (viewport.width <= 0 || height <= 0 || gap < 0 || height.toLong() + gap * 2L > viewport.height) return null
    val overlaps = dialog != null && dialog.right > viewport.left && dialog.left < viewport.right &&
        dialog.bottom > viewport.top && dialog.top < viewport.bottom
    val top = if (overlaps) minOf(viewport.bottom - gap, dialog.top - gap) - height else viewport.top + gap
    return top.takeIf { it >= viewport.top + gap && it.toLong() + height <= viewport.bottom - gap }
}

/** Window dimensions include root padding; the returned rectangle describes only the panel. */
internal fun reconstructFeedbackDialogBounds(
    viewport: FeedbackBounds, windowWidth: Int, windowHeight: Int,
    insetLeft: Int, insetTop: Int, insetRight: Int, insetBottom: Int, centered: Boolean
): FeedbackBounds? {
    if (windowWidth !in 1..viewport.width || windowHeight !in 1..viewport.height ||
        minOf(insetLeft, insetTop, insetRight, insetBottom) < 0 ||
        insetLeft.toLong() + insetRight >= windowWidth || insetTop.toLong() + insetBottom >= windowHeight) return null
    val left = viewport.left + (viewport.width - windowWidth) / 2
    val top = if (centered) viewport.top + (viewport.height - windowHeight) / 2 else viewport.bottom - windowHeight
    return FeedbackBounds(left + insetLeft, top + insetTop, left + windowWidth - insetRight, top + windowHeight - insetBottom)
}
