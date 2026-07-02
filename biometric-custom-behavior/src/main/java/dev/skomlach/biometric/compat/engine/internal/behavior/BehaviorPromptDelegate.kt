package dev.skomlach.biometric.compat.engine.internal.behavior

import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptHost

internal class BehaviorPromptDelegate(
    private val host: SoftwareBiometricPromptHost
) : SoftwareBiometricPromptDelegate {
    private val controller = host.rootView?.let { rootView ->
        BehaviorCaptureController(
            rootView = rootView,
            builder = host.builder,
            enroll = host.enroll
        ) {
            host.callbacks.onReady(host.builder.getExtras())
        }
    }

    override fun shouldInstall(): Boolean =
        shouldInstallBehaviorPrompt(
            authMode = host.builder.getBehaviorAuthMode(),
            hasController = controller != null
        )

    override fun install() {
        controller?.install()
    }

    override fun cancel() {
        controller?.dispose()
    }

    override fun dispose() {
        controller?.dispose()
    }

    override fun isReadyToStartAuth(): Boolean {
        return isBehaviorPromptReady(
            authMode = host.builder.getBehaviorAuthMode(),
            hasController = controller != null,
            hasPreparedPayload = controller?.consumePrepared() == true
        )
    }
}
