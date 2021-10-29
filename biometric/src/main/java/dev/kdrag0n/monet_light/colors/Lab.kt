package dev.kdrag0n.monet_light.colors

// Interface for Lab complementary color spaces
interface Lab : Color {
    val L: Double
    val a: Double
    val b: Double
}
