package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

import android.content.Context
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptFactory
import dev.skomlach.biometric.compat.custom.SoftwareBiometricProvider
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule

/**
 * Atomic voice provider backed by a locally supplied sherpa-onnx runtime and
 * speaker-embedding model. The manager, capture flow, prompt, storage and
 * lockout implementation live in this module; no VoiceAuth module is needed.
 */
class SherpaOnnxProvider : SoftwareBiometricProvider() {
    companion object {
        private const val MODULE_ID = 1_000_000_101
        internal const val PROMPT_FACTORY_PRIORITY = 1
        internal const val MODULE_PRIORITY =
            BiometricModule.PRIORITY_BELOW_SYSTEM_HARDWARE + 1
    }

    override val moduleId: Int = MODULE_ID
    override val promptFactoryPriority: Int = PROMPT_FACTORY_PRIORITY

    override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager =
        SherpaOnnxBiometricManager(
            context = context.applicationContext,
            engine = SherpaOnnxVoiceEngine(context.applicationContext),
            modulePriority = MODULE_PRIORITY
        )

    override fun getPromptFactory(): SoftwareBiometricPromptFactory = SherpaOnnxPromptFactory()
}
