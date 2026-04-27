package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import android.content.Context
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricProvider

class ZkFingerProvider : SoftwareBiometricProvider() {
    override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
        return ZkFingerUnlockManager(context.applicationContext)
    }
}
