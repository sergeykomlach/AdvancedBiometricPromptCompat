package dev.skomlach.biometric.compat.engine.internal

import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricTerminalState

/** Provider revocation is terminal even when the caller's cancellation signal is still active. */
internal abstract class SoftwareBiometricSessionCallback(
    protected val callbackGate: SoftwareBiometricCallbackGate,
    private val onProviderCanceled: () -> Unit
) : AbstractSoftwareBiometricManager.AuthenticationCallback() {
    final override fun onAuthenticationCancelled() {
        callbackGate.terminate(SoftwareBiometricTerminalState.CANCELLED, onProviderCanceled)
    }
}
