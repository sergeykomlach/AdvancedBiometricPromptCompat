package dev.skomlach.biometric.compat.engine.internal.voice

import android.content.Context
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptFactory
import dev.skomlach.biometric.compat.custom.SoftwareBiometricProvider

class VoiceProvider : SoftwareBiometricProvider() {
    companion object {
        // Historical manager-name hash. Keep this value when renaming or obfuscating classes.
        private const val MODULE_ID = 329_184_405
    }

    override val moduleId: Int = MODULE_ID

    override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
        return VoiceBiometricManager(
            context = context.applicationContext,
            engine = VoiceEngineSelector.create()
        )
    }

    override fun getPromptFactory(): SoftwareBiometricPromptFactory {
        return VoicePromptFactory()
    }
}
