package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptDelegate
import dev.skomlach.biometric.compat.custom.BackgroundSoftwareBiometricEnrollmentPromptFactory
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptHost

class SherpaOnnxPromptFactory : BackgroundSoftwareBiometricEnrollmentPromptFactory {
    override val biometricType: BiometricType = BiometricType.BIOMETRIC_VOICE
    override val requiresReadyExtrasBeforeAuthentication: Boolean = true

    override fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate? {
        // Voice capture must be bound to the manager selected for this provider runtime.
        return null
    }

    override fun create(
        host: SoftwareBiometricPromptHost,
        manager: AbstractSoftwareBiometricManager
    ): SoftwareBiometricPromptDelegate? {
        return (manager as? SherpaOnnxBiometricManager)?.let { voiceManager ->
            SherpaOnnxPromptDelegate(host, voiceManager)
        }
    }
}

