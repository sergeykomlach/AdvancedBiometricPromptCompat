package dev.skomlach.biometric.compat.monetsupport.utils.colors

// Interface for Lab complementary color spaces
interface Lab : Color {
    val L: Double
    val a: Double
    val b: Double
}
