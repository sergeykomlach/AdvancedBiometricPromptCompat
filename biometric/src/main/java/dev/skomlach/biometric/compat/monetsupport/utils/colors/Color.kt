package dev.skomlach.biometric.compat.monetsupport.utils.colors

interface Color {
    // All colors should have a conversion path to linear sRGB
    fun toLinearSrgb(): LinearSrgb
}
