package dev.skomlach.biometric.compat.custom

import dev.skomlach.biometric.compat.BiometricType

interface SoftwareBiometricPromptFactory {
    val biometricType: BiometricType
    val requiresReadyExtrasBeforeAuthentication: Boolean
        get() = false

    fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate?
}
