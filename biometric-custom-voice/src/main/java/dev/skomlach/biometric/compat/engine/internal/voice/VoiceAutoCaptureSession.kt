package dev.skomlach.biometric.compat.engine.internal.voice

import android.os.Bundle
import android.os.SystemClock
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricType

internal class VoiceAutoCaptureSession(
    private val enroll: Boolean,
    existingExtras: Bundle?,
    private val phrase: CharSequence?,
    private val callback: Callback,
    private val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    private val promptMessageResolver: (VoicePromptState, CharSequence?) -> VoicePromptRender = VoicePromptMessageResolver::resolve,
    private val outcomePolicy: VoiceOutcomePolicy = VoiceOutcomePolicy(),
    private val onCaptureLockout: () -> AuthenticationResult = {
        AuthenticationResult(
            type = BiometricType.BIOMETRIC_VOICE,
            reason = AuthenticationFailureReason.LOCKED_OUT,
            description = ""
        )
    }
) {
    interface Callback {
        fun onHelp(message: CharSequence) = Unit
        fun onReady(extras: Bundle)
        fun onError(result: AuthenticationResult)
        fun isPromptActive(): Boolean
        fun onStateChanged(state: VoicePromptState) = Unit
        fun onPromptUpdated(state: VoicePromptState, render: VoicePromptRender) {
            onStateChanged(state)
            onHelp(render.asHelpMessage())
        }
    }

    class Messages(
        private val authStart: CharSequence,
        private val voiceDetected: CharSequence,
        private val processing: CharSequence,
        private val enrollRecordingStarted: (Int, Int) -> CharSequence,
        private val sampleSavedTemplate: (Int, Int) -> CharSequence
    ) {
        fun recordingStarted(current: Int, total: Int, enroll: Boolean): CharSequence {
            return if (enroll) {
                enrollRecordingStarted(current, total)
            } else {
                authStart
            }
        }

        fun sampleSaved(current: Int, total: Int): CharSequence = sampleSavedTemplate(current, total)

        fun voiceDetected(): CharSequence = voiceDetected

        fun processing(): CharSequence = processing
    }

    private val preservedExtras = Bundle(existingExtras ?: Bundle())
    private val capturedSamples = ArrayList<FloatArray>()
    private var preparedExtras: Bundle? = null
    private var currentState: VoicePromptState? = null
    private var progress = VoiceOutcomeProgress(
        suspiciousCaptureAttempts = 0,
        lastProgressAtMs = 0L
    )
    private var terminal = false

    fun shouldAutoCapture(): Boolean = !hasVoiceInput(preservedExtras)

    fun start(nowMs: Long = SystemClock.elapsedRealtime()) {
        if (!shouldAutoCapture() || !callback.isPromptActive()) {
            return
        }
        terminal = false
        progress = VoiceOutcomeProgress(
            suspiciousCaptureAttempts = 0,
            lastProgressAtMs = nowMs
        )
        emitState(initialInstructionState())
    }

    fun onCaptureOutcome(outcome: VoiceCaptureOutcome, nowMs: Long) {
        if (!callback.isPromptActive() || terminal) {
            return
        }
        when (outcome) {
            is VoiceCaptureOutcome.Accepted -> handleAccepted(outcome, nowMs)
            is VoiceCaptureOutcome.Rejected -> handleRejected(outcome.decision, nowMs)
            VoiceCaptureOutcome.Timeout -> handleTimeout()
            is VoiceCaptureOutcome.Fatal -> {
                emitTerminalError(
                    state = null,
                    result = AuthenticationResult(
                        type = BiometricType.BIOMETRIC_VOICE,
                        reason = AuthenticationFailureReason.INTERNAL_ERROR,
                        description = outcome.message
                    )
                )
            }
        }
    }

    fun isReadyToStartAuth(): Boolean = preparedExtras != null

    fun shouldContinueCapture(): Boolean = shouldAutoCapture() && !terminal && preparedExtras == null

    fun shouldEmitTimeoutOutcome(nowMs: Long): Boolean {
        return !terminal && outcomePolicy.isTimedOut(progress.lastProgressAtMs, nowMs)
    }

    fun collectedSampleCount(): Int = capturedSamples.size

    fun preparedExtras(): Bundle? = preparedExtras?.let { Bundle(it) }

    fun currentPromptState(): VoicePromptState? = currentState

    fun dispose() {
        clearTransientState()
        terminal = true
    }

    private fun handleAccepted(
        outcome: VoiceCaptureOutcome.Accepted,
        nowMs: Long
    ) {
        progress = VoiceOutcomeProgress(
            suspiciousCaptureAttempts = 0,
            lastProgressAtMs = nowMs
        )
        if (outcome.hadSpeechActivity) {
            emitState(VoicePromptState.SpeechDetected)
        }
        emitState(VoicePromptState.ProcessingCapture)
        capturedSamples += outcome.sample.copyOf()
        if (capturedSamples.size < requiredSamples()) {
            emitState(nextInstructionState(retryReason = null))
            return
        }

        preparedExtras = buildVoiceExtras(
            existing = preservedExtras,
            phrase = phrase,
            sampleRateHz = sampleRateHz,
            pcmSamples = capturedSamples
        )
        emitState(VoicePromptState.Matching)
        callback.onReady(preparedExtras ?: Bundle())
    }

    private fun handleRejected(
        decision: VoiceCaptureDecision,
        nowMs: Long
    ) {
        if (outcomePolicy.isTimedOut(progress.lastProgressAtMs, nowMs)) {
            handleTimeout()
            return
        }
        when (val policyDecision = outcomePolicy.onCaptureRejected(progress, decision, nowMs)) {
            is VoiceOutcomeDecision.Retry -> {
                progress = policyDecision.progress
                emitState(nextInstructionState(retryReason = decision.toRetryReason()))
            }

            is VoiceOutcomeDecision.Lockout -> {
                progress = policyDecision.progress
                emitTerminalError(
                    state = VoicePromptState.Lockout,
                    result = onCaptureLockout()
                )
            }
        }
    }

    private fun handleTimeout() {
        val render = emitState(VoicePromptState.Timeout)
        emitTerminalError(
            state = null,
            result = AuthenticationResult(
                type = BiometricType.BIOMETRIC_VOICE,
                reason = AuthenticationFailureReason.TIMEOUT,
                description = render.primaryMessage
            )
        )
    }

    private fun emitTerminalError(
        state: VoicePromptState?,
        result: AuthenticationResult
    ) {
        state?.let { emitState(it) }
        terminal = true
        callback.onError(result)
    }

    private fun emitState(state: VoicePromptState): VoicePromptRender {
        currentState = state
        val render = promptMessageResolver(state, phrase)
        callback.onPromptUpdated(state, render)
        return render
    }

    private fun clearTransientState() {
        capturedSamples.clear()
        preparedExtras = null
        currentState = null
        progress = VoiceOutcomeProgress(
            suspiciousCaptureAttempts = 0,
            lastProgressAtMs = 0L
        )
    }

    private fun initialInstructionState(): VoicePromptState {
        return if (enroll) {
            VoicePromptState.EnrollInstruction(
                step = 1,
                total = requiredSamples(),
                retryReason = null
            )
        } else {
            VoicePromptState.AuthInstruction(retryReason = null)
        }
    }

    private fun nextInstructionState(retryReason: VoiceRetryReason?): VoicePromptState {
        return if (enroll) {
            VoicePromptState.EnrollInstruction(
                step = capturedSamples.size + 1,
                total = requiredSamples(),
                retryReason = retryReason
            )
        } else {
            VoicePromptState.AuthInstruction(retryReason = retryReason)
        }
    }

    private fun requiredSamples(): Int = if (enroll) ENROLLMENT_SAMPLE_COUNT else 1

    private fun VoiceCaptureDecision.toRetryReason(): VoiceRetryReason {
        return when (rejectReason) {
            VoiceCaptureRejectReason.RECORDER_FAILURE -> VoiceRetryReason.RECORDING_FAILED
            VoiceCaptureRejectReason.NO_SPEECH -> VoiceRetryReason.NO_SPEECH
            VoiceCaptureRejectReason.INCOMPLETE_SAMPLE -> VoiceRetryReason.SAMPLE_TOO_SHORT
            VoiceCaptureRejectReason.NONE -> VoiceRetryReason.RECORDING_FAILED
            VoiceCaptureRejectReason.QUALITY_ISSUE -> {
                when (qualityIssue) {
                    VoiceQualityIssue.SAMPLE_TOO_SHORT -> VoiceRetryReason.SAMPLE_TOO_SHORT
                    VoiceQualityIssue.SAMPLE_TOO_QUIET -> VoiceRetryReason.SAMPLE_TOO_QUIET
                    VoiceQualityIssue.SAMPLE_TOO_FLAT -> VoiceRetryReason.SAMPLE_TOO_FLAT
                    VoiceQualityIssue.SAMPLE_TOO_LONG -> VoiceRetryReason.SAMPLE_TOO_LONG
                    VoiceQualityIssue.SAMPLE_REPLAY_RISK -> VoiceRetryReason.SAMPLE_REPLAY_RISK
                    else -> VoiceRetryReason.RECORDING_FAILED
                }
            }
        }
    }

    private companion object {
        const val ENROLLMENT_SAMPLE_COUNT = 3
        const val DEFAULT_SAMPLE_RATE_HZ = 16_000
    }
}

internal fun VoicePromptRender.asHelpMessage(): String {
    return listOfNotNull(
        primaryMessage.takeIf { it.isNotBlank() },
        secondaryMessage?.takeIf { it.isNotBlank() }
    ).joinToString(separator = "\n")
}
