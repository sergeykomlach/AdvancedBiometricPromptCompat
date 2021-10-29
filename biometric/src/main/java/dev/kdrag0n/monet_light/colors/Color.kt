package dev.kdrag0n.monet_light.colors

interface Color {
    // All colors should have a conversion path to linear sRGB
    fun toLinearSrgb(): LinearSrgb
}
