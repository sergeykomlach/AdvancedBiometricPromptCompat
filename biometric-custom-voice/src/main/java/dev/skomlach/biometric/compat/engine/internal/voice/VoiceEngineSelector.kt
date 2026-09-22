package dev.skomlach.biometric.compat.engine.internal.voice

import dev.skomlach.biometric.compat.custom.SoftwareBiometricServices
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

internal object VoiceEngineSelector {
    fun create(): VoiceEngine {
        return select(ServiceLoader.load(VoiceEngineProvider::class.java))
    }

    fun select(providers: Iterable<VoiceEngineProvider>): VoiceEngine {
        return try {
            SoftwareBiometricServices.collect(providers, {}) { provider ->
                val engine = provider.createEngine()
                if (engine.isAvailable()) provider.priority to engine else null
            }.maxByOrNull { (priority, _) -> priority }?.second ?: CepstralVoiceEngine()
        } catch (_: ServiceConfigurationError) {
            CepstralVoiceEngine()
        }
    }
}
