package dev.skomlach.biometric.compat.engine

import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.BiometricModule

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface BiometricInitListener {
    fun initFinished(method: BiometricMethod, module: BiometricModule?)
    fun onBiometricReady()
}