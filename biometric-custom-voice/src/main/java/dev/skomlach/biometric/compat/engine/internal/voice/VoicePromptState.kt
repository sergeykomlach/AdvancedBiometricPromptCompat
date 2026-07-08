package dev.skomlach.biometric.compat.engine.internal.voice

internal sealed interface VoicePromptState {
    data class EnrollInstruction(
        val step: Int,
        val total: Int,
        val retryReason: VoiceRetryReason?
    ) : VoicePromptState

    data class AuthInstruction(
        val retryReason: VoiceRetryReason?
    ) : VoicePromptState

    data object Listening : VoicePromptState

    data object SpeechDetected : VoicePromptState

    data object ProcessingCapture : VoicePromptState

    data object Matching : VoicePromptState

    data object Timeout : VoicePromptState

    data object Lockout : VoicePromptState
}

internal enum class VoiceRetryReason {
    NO_SPEECH,
    SAMPLE_TOO_SHORT,
    SAMPLE_TOO_QUIET,
    SAMPLE_TOO_FLAT,
    SAMPLE_TOO_LONG,
    SAMPLE_REPLAY_RISK,
    RECORDING_FAILED
}

internal data class VoicePromptRender(
    val primaryMessage: String,
    val secondaryMessage: String? = null
)
