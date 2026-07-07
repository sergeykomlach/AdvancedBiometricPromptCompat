package dev.skomlach.biometric.compat.engine.internal.voice

import android.os.Bundle
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptHost

internal class VoicePromptDelegate(
    private val host: SoftwareBiometricPromptHost
) : SoftwareBiometricPromptDelegate {
    private val lockoutManager = VoiceBiometricManager(host.context)
    private val controller = VoiceAutoCaptureController(
        context = host.context,
        builder = host.builder,
        enroll = host.enroll,
        callback = object : VoiceAutoCaptureSession.Callback {
            override fun onHelp(message: CharSequence) {
                host.callbacks.onHelp(message)
            }

            override fun onReady(extras: Bundle) {
                host.callbacks.onReady(extras)
            }

            override fun onError(result: AuthenticationResult) {
                host.callbacks.onFailure(result)
            }

            override fun isPromptActive(): Boolean = host.callbacks.isPromptActive()
        },
        onMaxAttemptsExceeded = { lockoutManager.triggerAutoCaptureLockout() }
    )

    override fun start() {
        if (controller.shouldAutoCapture()) {
            controller.start()
        } else {
            host.callbacks.onReady(null)
        }
    }

    override fun cancel() {
        controller.dispose()
    }

    override fun dispose() {
        controller.dispose()
    }

    override fun isReadyToStartAuth(): Boolean = controller.isReadyToStartAuth()
}
