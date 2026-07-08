package dev.skomlach.biometric.compat.engine.internal.voice

import dev.skomlach.biometric.custom.voice.R
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.translate.LocalizationHelper

internal object VoicePromptMessageResolver {
    private data class PromptTemplate(
        val resourceId: Int,
        val fallback: String
    )

    private val enrollFirstInstruction = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_enroll_first_instruction,
        fallback = "To register your voice, say your code phrase naturally for 2-4 seconds. Attempt %1\$d of %2\$d."
    )
    private val enrollRepeatInstruction = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_enroll_repeat_instruction,
        fallback = "Repeat the same code phrase again. Attempt %1\$d of %2\$d."
    )
    private val authInstruction = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_auth_instruction,
        fallback = "Say your code phrase naturally."
    )
    private val phraseTemplate = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_phrase,
        fallback = "Phrase: %1\$s"
    )
    private val listening = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_listening,
        fallback = "Listening for voice"
    )
    private val speechDetected = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_detected,
        fallback = "Voice detected"
    )
    private val processingCapture = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_processing,
        fallback = "Processing voice sample"
    )
    private val matching = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_matching,
        fallback = "Checking voice"
    )
    private val timeout = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_timeout,
        fallback = "Voice authentication timed out"
    )
    private val lockout = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_lockout,
        fallback = "Voice authentication is temporarily locked"
    )
    private val retryNoSpeech = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_retry_no_speech,
        fallback = "No speech detected. Try the same phrase again."
    )
    private val retrySampleTooShort = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_retry_sample_too_short,
        fallback = "The phrase was too short. Repeat the same phrase again."
    )
    private val retrySampleTooQuiet = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_retry_sample_too_quiet,
        fallback = "The phrase was too quiet. Move closer to the microphone and try again."
    )
    private val retrySampleTooFlat = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_retry_sample_too_flat,
        fallback = "Speak the phrase more clearly and try again."
    )
    private val retrySampleTooLong = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_retry_sample_too_long,
        fallback = "Keep the phrase shorter and try again."
    )
    private val retrySampleReplayRisk = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_retry_sample_replay_risk,
        fallback = "Use a fresh natural phrase and try again."
    )
    private val retryRecordingFailed = PromptTemplate(
        resourceId = R.string.biometriccompat_voice_prompt_retry_recording_failed,
        fallback = "Recording failed. Try the same phrase again."
    )

    fun resolve(state: VoicePromptState, phrase: CharSequence?): VoicePromptRender {
        return resolve(state, phrase, ::resolveLocalizedString)
    }

    internal fun resolve(
        state: VoicePromptState,
        phrase: CharSequence?,
        stringResolver: (Int, Array<out Any?>) -> String?
    ): VoicePromptRender {
        return when (state) {
            is VoicePromptState.EnrollInstruction -> {
                val instruction = if (state.step == 1) {
                    enrollFirstInstruction.format(stringResolver, state.step, state.total)
                } else {
                    enrollRepeatInstruction.format(stringResolver, state.step, state.total)
                }
                VoicePromptRender(
                    primaryMessage = appendPhrase(instruction, phrase, stringResolver),
                    secondaryMessage = retryText(state.retryReason, stringResolver)
                )
            }

            is VoicePromptState.AuthInstruction -> VoicePromptRender(
                primaryMessage = appendPhrase(authInstruction.format(stringResolver), phrase, stringResolver),
                secondaryMessage = retryText(state.retryReason, stringResolver)
            )

            VoicePromptState.Listening -> VoicePromptRender(listening.format(stringResolver))
            VoicePromptState.SpeechDetected -> VoicePromptRender(speechDetected.format(stringResolver))
            VoicePromptState.ProcessingCapture -> VoicePromptRender(processingCapture.format(stringResolver))
            VoicePromptState.Matching -> VoicePromptRender(matching.format(stringResolver))
            VoicePromptState.Timeout -> VoicePromptRender(timeout.format(stringResolver))
            VoicePromptState.Lockout -> VoicePromptRender(lockout.format(stringResolver))
        }
    }

    private fun appendPhrase(
        message: String,
        phrase: CharSequence?,
        stringResolver: (Int, Array<out Any?>) -> String?
    ): String {
        val normalizedPhrase = phrase
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return message
        return "$message ${phraseTemplate.format(stringResolver, normalizedPhrase)}"
    }

    private fun retryText(
        reason: VoiceRetryReason?,
        stringResolver: (Int, Array<out Any?>) -> String?
    ): String? {
        return when (reason) {
            null -> null
            VoiceRetryReason.NO_SPEECH -> retryNoSpeech.format(stringResolver)
            VoiceRetryReason.SAMPLE_TOO_SHORT -> retrySampleTooShort.format(stringResolver)
            VoiceRetryReason.SAMPLE_TOO_QUIET -> retrySampleTooQuiet.format(stringResolver)
            VoiceRetryReason.SAMPLE_TOO_FLAT -> retrySampleTooFlat.format(stringResolver)
            VoiceRetryReason.SAMPLE_TOO_LONG -> retrySampleTooLong.format(stringResolver)
            VoiceRetryReason.SAMPLE_REPLAY_RISK -> retrySampleReplayRisk.format(stringResolver)
            VoiceRetryReason.RECORDING_FAILED -> retryRecordingFailed.format(stringResolver)
        }
    }

    private fun PromptTemplate.format(
        stringResolver: (Int, Array<out Any?>) -> String?,
        vararg args: Any?
    ): String {
        stringResolver(resourceId, args).takeIf { !it.isNullOrBlank() }?.let { return it }
        return fallback.format(*args)
    }

    private fun resolveLocalizedString(resourceId: Int, formatArgs: Array<out Any?>): String? {
        val localized = runCatching {
            LocalizationHelper.getLocalizedString(AndroidContext.appContext, resourceId, *formatArgs)
        }.getOrNull()
        return localized?.takeIf { it.isNotBlank() }
    }
}
