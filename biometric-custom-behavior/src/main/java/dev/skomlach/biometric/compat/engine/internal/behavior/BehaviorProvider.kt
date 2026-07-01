package dev.skomlach.biometric.compat.engine.internal.behavior

import android.content.Context
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricProvider

class BehaviorProvider : SoftwareBiometricProvider() {
    override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
        return BehaviorBiometricManager(context.applicationContext)
    }
}
