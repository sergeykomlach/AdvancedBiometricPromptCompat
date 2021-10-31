package dev.skomlach.biometric.compat.monetsupport.extensions

import dev.skomlach.biometric.compat.monetsupport.utils.colors.Color as MonetColor

/**
 *  To avoid editing the core Monet code by kdrag0n, these are extensions instead
 */
fun MonetColor.toArgb(): Int {
    return toLinearSrgb().toSrgb().quantize8()
}