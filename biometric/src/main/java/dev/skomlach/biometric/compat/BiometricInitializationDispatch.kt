package dev.skomlach.biometric.compat

/**
 * Discovery may load classes, initialize SDKs and read storage. Keep it off the UI queue,
 * but do not release callers until registration and cache invalidation have completed.
 */
internal fun completeBiometricInitialization(
    dispatchBackground: (Runnable) -> Unit,
    dispatchMain: (Runnable) -> Unit,
    loadSoftware: () -> Unit,
    onReady: () -> Unit
) {
    dispatchBackground(Runnable {
        loadSoftware()
        dispatchMain(Runnable { onReady() })
    })
}
