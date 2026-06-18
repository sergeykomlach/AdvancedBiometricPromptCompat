package dev.skomlach.biometric.compat.engine.internal.voice

interface VoiceEngineProvider {
    val priority: Int
    fun createEngine(): VoiceEngine
}
