package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkSession

/** Ownership lasts until result delivery or cancellation, not merely until camera close. */
internal class FaceSessionOwner {
    private var operation: SoftwareBiometricWorkSession? = null
    private var cancel: (() -> Unit)? = null

    @Synchronized fun claim(next: SoftwareBiometricWorkSession, cancelNext: () -> Unit) {
        cancel?.invoke()
        operation = next
        cancel = cancelNext
    }

    @Synchronized fun release(expected: SoftwareBiometricWorkSession) {
        if (operation === expected) {
            operation = null
            cancel = null
        }
    }

    @Synchronized fun revoke(remove: () -> Unit) {
        val cancelCurrent = cancel
        operation = null
        cancel = null
        cancelCurrent?.invoke()
        // Invalidation waits for an in-flight commit. Keep replacement out until
        // persistence has been updated, including when another manager removes it.
        remove()
    }

    @Synchronized fun owns(expected: SoftwareBiometricWorkSession): Boolean = operation === expected
}
