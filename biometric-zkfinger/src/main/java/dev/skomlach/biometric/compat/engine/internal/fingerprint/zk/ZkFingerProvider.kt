package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import android.content.Context
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricProvider
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptFactory

class ZkFingerProvider : SoftwareBiometricProvider() {
    companion object {
        // Historical manager-name hash. Keep this value when renaming or obfuscating classes.
        private const val MODULE_ID = 1_962_625_682
    }

    override val moduleId: Int = MODULE_ID

    override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
        return ZkFingerUnlockManager(context.applicationContext)
    }

    override fun getPromptFactory(): SoftwareBiometricPromptFactory {
        return ZkFingerPromptFactory()
    }
}
