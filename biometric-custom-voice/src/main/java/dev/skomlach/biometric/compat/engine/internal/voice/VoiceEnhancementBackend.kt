package dev.skomlach.biometric.compat.engine.internal.voice

internal fun interface VoiceEnhancementBackend {
    fun enhance(pcm: FloatArray, sampleRateHz: Int): FloatArray
}

internal enum class VoiceBackendType {
    HEURISTIC,

    // WebRTC APM fits the real-time mic path when we need AGC + NS + AEC together.
    WEBRTC_APM,

    // RNNoise is a narrow denoise-only front-end when we want cleanup without VAD policy.
    RNNOISE,

    // Silero VAD is the stronger speech/non-speech detector when heuristic gating is not enough.
    SILERO_VAD
}
