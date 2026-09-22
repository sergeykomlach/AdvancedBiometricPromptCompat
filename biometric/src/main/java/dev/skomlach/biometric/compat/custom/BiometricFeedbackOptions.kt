package dev.skomlach.biometric.compat.custom

/** Foreground hints are app UI, not an overlay permission or a replacement for system auth UI. */
data class BiometricFeedbackOptions(
    val enabled: Boolean = true,
    val timeoutMillis: Long = 4_000L,
    val animation: Animation = Animation.FADE
) {
    init { require(timeoutMillis in 1_000L..60_000L) }
    enum class Animation { NONE, FADE, FOLD }
}
