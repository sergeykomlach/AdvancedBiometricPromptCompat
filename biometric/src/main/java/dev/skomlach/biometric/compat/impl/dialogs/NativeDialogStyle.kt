package dev.skomlach.biometric.compat.impl.dialogs

/** Values only: the process cache must never retain a foreign Context, Drawable or View. */
internal data class NativeDialogStyle(
    val source: String,
    val border: Int,
    val corner: Int,
    val gravity: Int,
    val contentWidth: Int?,
    val title: NativeTextStyle,
    val subtitle: NativeTextStyle,
    val description: NativeTextStyle,
    val button: NativeTextStyle,
    val indicator: NativeTextStyle?,
    val iconWidth: Int?,
    val iconHeight: Int?,
    val iconTop: Int,
    val iconBottom: Int,
    val centeredButton: Boolean,
    val outerInsets: NativeInsets = NativeInsets(border, border, border, border),
    val buttonBarHeight: Int? = null,
    val modern: NativeDialogModernStyle? = null,
    val buttonBarPadding: NativeInsets = NativeInsets(),
    // The XML declares 0dp and external code supplies the width: preserve our local card sizing.
    val preserveLocalCardWidth: Boolean = false
) {
    fun windowWidth(available: Int, fallbackMaxWidth: Int): Int {
        val maximum = contentWidth?.let { it.toLong() + outerInsets.start + outerInsets.end }
            ?: (if (modern != null) available.toLong() else null)
            ?: fallbackMaxWidth.takeIf { it > 0 }?.toLong()
            ?: available.toLong()
        return maximum.coerceIn(1L, available.coerceAtLeast(1).toLong()).toInt()
    }

    fun fitsWindow(width: Int): Boolean = width.toLong() > outerInsets.start.toLong() + outerInsets.end + 1

    fun isValid(density: Float, scaledDensity: Float): Boolean {
        if (!density.isFinite() || density <= 0 || !scaledDensity.isFinite() || scaledDensity <= 0) return false
        fun spacing(value: Int) = value >= 0 && value <= 96 * density
        fun icon(value: Int?) = value == null || value in (12 * density).toInt()..(240 * density).toInt()
        return spacing(border) && spacing(corner) && spacing(iconTop) && spacing(iconBottom) &&
            (gravity == 17 || gravity == 81) &&
            (contentWidth == null || contentWidth in (160 * density).toInt()..(1000 * density).toInt()) &&
            icon(iconWidth) && icon(iconHeight) && outerInsets.isValid(density) &&
            (buttonBarHeight == null || buttonBarHeight in 1..(120 * density).toInt()) &&
            buttonBarPadding.isValid(density) && (buttonBarHeight == null ||
                buttonBarPadding.top.toLong() + buttonBarPadding.bottom < buttonBarHeight) &&
            (modern == null || modern.isValid(density, scaledDensity)) &&
            listOfNotNull(title, subtitle, description, button, indicator).all { it.isValid(density, scaledDensity) }
    }
}

internal data class NativeDialogModernStyle(
    val twoPane: Boolean,
    val contentPadding: NativeInsets,
    val panelPadding: NativeInsets,
    val logoWidth: Int,
    val logoHeight: Int,
    val logoDescription: NativeTextStyle,
    val buttonMinHeight: Int?
) {
    fun isValid(density: Float, scaledDensity: Float) =
        contentPadding.isValid(density) && panelPadding.isValid(density) &&
            logoWidth in 1..(96 * density).toInt() && logoHeight in 1..(96 * density).toInt() &&
            (buttonMinHeight == null || buttonMinHeight in 1..(96 * density).toInt()) &&
            logoDescription.isValid(density, scaledDensity)
}

internal data class NativeTextStyle(
    val size: Float?,
    val gravity: Int?,
    val padding: NativeInsets,
    val margin: NativeInsets,
    val includeFontPadding: Boolean?,
    val allCaps: Boolean?,
    val textStyle: Int?
) {
    fun isValid(density: Float, scaledDensity: Float): Boolean =
        (size == null || size.isFinite() && size >= 8 * scaledDensity && size <= 48 * scaledDensity) &&
            padding.isValid(density) && margin.isValid(density) &&
            (textStyle == null || textStyle in 0..3)
}

internal data class NativeInsets(val start: Int = 0, val top: Int = 0, val end: Int = 0, val bottom: Int = 0) {
    fun isValid(density: Float): Boolean = listOf(start, top, end, bottom).all { it >= 0 && it <= 96 * density }
}
