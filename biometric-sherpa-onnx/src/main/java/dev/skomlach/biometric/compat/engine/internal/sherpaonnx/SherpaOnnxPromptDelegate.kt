package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

import android.os.Bundle
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptHost
import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus

internal class SherpaOnnxPromptDelegate(
    private val host: SoftwareBiometricPromptHost,
    private val lockoutManager: SherpaOnnxBiometricManager
) : SoftwareBiometricPromptDelegate {
    private var disposed = false

    private val controller = VoiceAutoCaptureController(
        context = host.context,
        builder = host.builder,
        enroll = host.enroll,
        callback = object : VoiceAutoCaptureSession.Callback {
            override fun onPromptUpdated(state: VoicePromptState, render: VoicePromptRender) {
                host.callbacks.onStatus(
                    render.toSoftwarePromptStatus(state)
                )
            }

            override fun onReady(extras: Bundle) {
                host.callbacks.onReady(extras)
            }

            override fun onError(result: AuthenticationResult) {
                host.callbacks.onFailure(result)
            }

            override fun isPromptActive(): Boolean = host.callbacks.isPromptActive()
        },
        onMaxAttemptsExceeded = lockoutManager::triggerAutoCaptureLockout
    )

    override fun start() {
        if (host.enroll) startCapture(null) else lockoutManager.checkEnrollment(::startCapture)
    }

    private fun startCapture(problem: AuthenticationResult?) {
        if (disposed || !host.callbacks.isPromptActive()) return
        if (problem != null) {
            problem.description?.let { message ->
                host.callbacks.onStatus(SoftwarePromptStatus(primaryText = message, terminal = true))
            }
            host.callbacks.onFailure(problem)
            return
        }
        if (controller.shouldAutoCapture()) {
            controller.start()
        } else {
            host.callbacks.onReady(null)
        }
    }

    override fun cancel() {
        dispose()
    }

    override fun dispose() {
        disposed = true
        controller.dispose()
    }

    override fun isReadyToStartAuth(): Boolean = controller.isReadyToStartAuth()

    private fun VoicePromptRender.toSoftwarePromptStatus(
        state: VoicePromptState
    ): SoftwarePromptStatus {
        return SoftwarePromptStatus(
            primaryText = primaryMessage,
            secondaryText = secondaryMessage,
            terminal = state == VoicePromptState.Timeout || state == VoicePromptState.Lockout,
            persistent = state is VoicePromptState.EnrollInstruction || state is VoicePromptState.AuthInstruction ||
                state == VoicePromptState.Listening || state == VoicePromptState.SpeechDetected
        )
    }
}

