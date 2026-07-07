package dev.skomlach.biometric.compat.engine.internal.voice

import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptFactory
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptHost

class VoicePromptFactory : SoftwareBiometricPromptFactory {
    override val biometricType: BiometricType = BiometricType.BIOMETRIC_VOICE
    override val requiresReadyExtrasBeforeAuthentication: Boolean = true

    override fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate? {
        return VoicePromptDelegate(host)
    }
}
