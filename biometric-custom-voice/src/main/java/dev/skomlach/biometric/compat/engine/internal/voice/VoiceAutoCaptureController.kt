package dev.skomlach.biometric.compat.engine.internal.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.utils.Vibro
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.common.translate.LocalizationHelper
import java.util.concurrent.atomic.AtomicBoolean

internal class VoiceAutoCaptureController(
    private val context: Context,
    private val builder: BiometricPromptCompat.Builder,
    enroll: Boolean,
    private val callback: VoiceAutoCaptureSession.Callback,
    private val onMaxAttemptsExceeded: () -> VoiceLockoutOutcome
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val disposed = AtomicBoolean(false)
    private val session = VoiceAutoCaptureSession(
        enroll = enroll,
        existingExtras = builder.getExtras(),
        phrase = builder.getVoicePhrase(),
        callback = callback,
        sampleRateHz = SAMPLE_RATE_HZ,
        onCaptureLockout = {
            val outcome = onMaxAttemptsExceeded()
            AuthenticationResult(
                type = BiometricType.BIOMETRIC_VOICE,
                reason = outcome.reason,
                description = localized(outcome.messageResId)
            )
        }
    )
    private val orchestrator = VoiceCaptureOrchestrator(
        sampleRateHz = SAMPLE_RATE_HZ,
        mainHandler = mainHandler,
        isPromptActive = callback::isPromptActive,
        onOutcome = ::onCaptureOutcome
    )

    fun shouldAutoCapture(): Boolean = session.shouldAutoCapture()

    fun isReadyToStartAuth(): Boolean = session.isReadyToStartAuth()

    fun start() {
        if (!shouldAutoCapture()) {
            return
        }
        session.start()
        startNextAttempt()
    }

    fun dispose() {
        disposed.set(true)
        orchestrator.cancel()
        session.dispose()
    }

    private fun startNextAttempt() {
        if (disposed.get() || !callback.isPromptActive() || !session.shouldContinueCapture()) {
            return
        }
        if (session.shouldEmitTimeoutOutcome(SystemClock.elapsedRealtime())) {
            session.onCaptureOutcome(
                outcome = VoiceCaptureOutcome.Timeout,
                nowMs = SystemClock.elapsedRealtime()
            )
            return
        }
        Vibro.start()
        orchestrator.start()
    }

    private fun onCaptureOutcome(outcome: VoiceCaptureOutcome) {
        if (disposed.get() || !callback.isPromptActive()) {
            return
        }
        if (outcome is VoiceCaptureOutcome.Rejected) {
            d(
                "VoiceAutoCaptureController.reject reason=${outcome.decision.rejectReason} " +
                    "quality=${outcome.decision.qualityIssue} speech=${outcome.decision.hadSpeechActivity}"
            )
        }
        if (outcome is VoiceCaptureOutcome.Timeout) {
            session.onCaptureOutcome(outcome, nowMs = SystemClock.elapsedRealtime())
            return
        }
        session.onCaptureOutcome(outcome, nowMs = SystemClock.elapsedRealtime())
        if (session.shouldContinueCapture()) {
            startNextAttempt()
        }
    }

    private fun localized(id: Int, vararg formatArgs: Any?): String {
        return LocalizationHelper.getLocalizedString(context, id, *formatArgs)
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
    }
}
