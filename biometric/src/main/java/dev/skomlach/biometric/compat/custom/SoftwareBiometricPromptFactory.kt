package dev.skomlach.biometric.compat.custom

import dev.skomlach.biometric.compat.BiometricType

interface SoftwareBiometricPromptFactory {
    val biometricType: BiometricType

    fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate?
}
