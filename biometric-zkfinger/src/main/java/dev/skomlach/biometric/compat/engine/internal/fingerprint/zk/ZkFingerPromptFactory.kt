package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.EngineBackedSoftwarePromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptFactory
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptHost
import dev.skomlach.biometric.zkfinger.R
import dev.skomlach.common.translate.LocalizationHelper

class ZkFingerPromptFactory : SoftwareBiometricPromptFactory {
    override val biometricType: BiometricType = BiometricType.BIOMETRIC_FINGERPRINT

    override fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate {
        return EngineBackedSoftwarePromptDelegate(host.enroll, host.callbacks) { enroll ->
            LocalizationHelper.getLocalizedString(
                host.context,
                if (enroll) {
                    R.string.biometriccompat_zkfinger_prompt_start_enroll
                } else {
                    R.string.biometriccompat_zkfinger_prompt_start_auth
                }
            )
        }
    }
}
