package dev.skomlach.biometric.compat.engine.internal.voice

import android.os.Bundle
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricType

internal class VoiceAutoCaptureSession(
    private val enroll: Boolean,
    existingExtras: Bundle?,
    private val phrase: CharSequence?,
    private val callback: Callback,
    private val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    private val messages: Messages
) {
    interface Callback {
        fun onHelp(message: CharSequence)
        fun onReady(extras: Bundle)
        fun onError(result: AuthenticationResult)
        fun isPromptActive(): Boolean
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
    private var speechDetectedForCurrentAttempt = false

    fun shouldAutoCapture(): Boolean = !hasVoiceInput(preservedExtras)

    fun start() {
        if (!shouldAutoCapture() || !callback.isPromptActive()) {
            return
        }
        callback.onHelp(messages.recordingStarted(currentSampleOrdinal(), requiredSamples(), enroll))
    }

    fun onSpeechDetected() {
        if (!speechDetectedForCurrentAttempt && callback.isPromptActive()) {
            speechDetectedForCurrentAttempt = true
            callback.onHelp(messages.voiceDetected())
        }
    }

    fun onRecoverableError(message: CharSequence) {
        if (!callback.isPromptActive()) {
            return
        }
        speechDetectedForCurrentAttempt = false
        callback.onHelp(message)
        callback.onHelp(messages.recordingStarted(currentSampleOrdinal(), requiredSamples(), enroll))
    }

    fun onSampleCaptured(sample: FloatArray) {
        if (!callback.isPromptActive()) {
            return
        }
        capturedSamples += sample.copyOf()
        speechDetectedForCurrentAttempt = false
        if (capturedSamples.size >= requiredSamples()) {
            preparedExtras = buildVoiceExtras(
                existing = preservedExtras,
                phrase = phrase,
                sampleRateHz = sampleRateHz,
                pcmSamples = capturedSamples
            )
            callback.onHelp(messages.processing())
            callback.onReady(preparedExtras ?: Bundle())
            return
        }

        callback.onHelp(messages.sampleSaved(capturedSamples.size, requiredSamples()))
        callback.onHelp(messages.recordingStarted(currentSampleOrdinal(), requiredSamples(), enroll))
    }

    fun onFatalError(reason: AuthenticationFailureReason, message: CharSequence) {
        if (!callback.isPromptActive()) {
            return
        }
        clearTransientState()
        callback.onError(
            AuthenticationResult(
                type = BiometricType.BIOMETRIC_VOICE,
                reason = reason,
                description = message
            )
        )
    }

    fun isReadyToStartAuth(): Boolean = preparedExtras != null

    fun collectedSampleCount(): Int = capturedSamples.size

    fun preparedExtras(): Bundle? = preparedExtras?.let { Bundle(it) }

    fun dispose() {
        clearTransientState()
    }

    private fun clearTransientState() {
        capturedSamples.clear()
        preparedExtras = null
        speechDetectedForCurrentAttempt = false
    }

    private fun currentSampleOrdinal(): Int = capturedSamples.size + 1

    private fun requiredSamples(): Int = if (enroll) ENROLLMENT_SAMPLE_COUNT else 1

    private companion object {
        const val ENROLLMENT_SAMPLE_COUNT = 3
        const val DEFAULT_SAMPLE_RATE_HZ = 16_000
    }
}
