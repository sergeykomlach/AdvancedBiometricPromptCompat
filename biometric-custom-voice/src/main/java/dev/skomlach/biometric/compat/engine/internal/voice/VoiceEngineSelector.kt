package dev.skomlach.biometric.compat.engine.internal.voice

import java.util.ServiceLoader

internal object VoiceEngineSelector {
    fun create(): VoiceEngine {
        return select(ServiceLoader.load(VoiceEngineProvider::class.java))
    }

    fun select(providers: Iterable<VoiceEngineProvider>): VoiceEngine {
        return providers
            .mapNotNull { provider ->
                runCatching {
                    provider.priority to provider.createEngine()
                }.getOrNull()
            }
            .filter { (_, engine) -> engine.isAvailable() }
            .maxByOrNull { (priority, _) -> priority }
            ?.second
            ?: CepstralVoiceEngine()
    }
}
