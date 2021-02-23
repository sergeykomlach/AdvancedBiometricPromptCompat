package dev.skomlach.biometric.compat.impl

import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.BiometricPromptCompat

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface IBiometricPromptImpl {
    fun authenticate(callback: BiometricPromptCompat.Result?)
    fun cancelAuthenticate()
    fun cancelAuthenticateBecauseOnPause(): Boolean
    val isNightMode: Boolean
    val builder: BiometricPromptCompat.Builder
    val usedPermissions: List<String>
}