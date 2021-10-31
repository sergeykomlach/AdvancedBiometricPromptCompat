package com.kieronquinn.monetcompat_light.extensions

import dev.kdrag0n.monet_light.colors.Color as MonetColor

/**
 *  To avoid editing the core Monet code by kdrag0n, these are extensions instead
 */
fun MonetColor.toArgb(): Int {
    return toLinearSrgb().toSrgb().quantize8()
}