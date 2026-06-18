package dev.skomlach.biometric.compat.engine.internal.voice

import android.content.Context
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricProvider

class VoiceProvider : SoftwareBiometricProvider() {
    override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
        return VoiceBiometricManager(
            context = context.applicationContext,
            engine = VoiceEngineSelector.create()
        )
    }
}
