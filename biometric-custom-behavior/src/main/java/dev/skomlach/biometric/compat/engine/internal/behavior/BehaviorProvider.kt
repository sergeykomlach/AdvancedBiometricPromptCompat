package dev.skomlach.biometric.compat.engine.internal.behavior

import android.content.Context
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptFactory
import dev.skomlach.biometric.compat.custom.SoftwareBiometricProvider

class BehaviorProvider : SoftwareBiometricProvider() {
    companion object {
        // Historical manager-name hash. Keep this value when renaming or obfuscating classes.
        private const val MODULE_ID = 1_511_761_485
    }

    override val moduleId: Int = MODULE_ID

    override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
        return BehaviorBiometricManager(context.applicationContext)
    }

    override fun getPromptFactory(): SoftwareBiometricPromptFactory {
        return BehaviorPromptFactory()
    }
}
