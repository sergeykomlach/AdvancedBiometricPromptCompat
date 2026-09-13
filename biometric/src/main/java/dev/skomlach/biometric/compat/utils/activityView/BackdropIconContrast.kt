package dev.skomlach.biometric.compat.utils.activityView

/** Compare the actual opaque theme shades using their relative luminance. */
internal fun shouldUseDarkBackdropIcons(
    backgroundLuminance: Double,
    lightIconLuminance: Double,
    darkIconLuminance: Double
): Boolean {
    fun contrast(iconLuminance: Double): Double =
        (maxOf(backgroundLuminance, iconLuminance) + 0.05) /
            (minOf(backgroundLuminance, iconLuminance) + 0.05)

    return contrast(darkIconLuminance) >= contrast(lightIconLuminance)
}

/** Apply newer completed samples even while the next sample is still being computed. */
internal class BackdropPaletteResults {
    private var sequence = 0L
    private var lastApplied = 0L

    fun nextRequest(): Long = ++sequence

    fun accept(request: Long): Boolean {
        if (request <= lastApplied || request > sequence) return false
        lastApplied = request
        return true
    }

    fun reset() {
        lastApplied = ++sequence
    }
}
