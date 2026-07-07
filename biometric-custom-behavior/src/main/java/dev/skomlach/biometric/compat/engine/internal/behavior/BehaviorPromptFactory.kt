package dev.skomlach.biometric.compat.engine.internal.behavior

import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptFactory
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptHost

class BehaviorPromptFactory : SoftwareBiometricPromptFactory {
    override val biometricType: BiometricType = BiometricType.BIOMETRIC_BEHAVIOR
    override val requiresReadyExtrasBeforeAuthentication: Boolean = true

    override fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate {
        return BehaviorPromptDelegate(host)
    }
}
