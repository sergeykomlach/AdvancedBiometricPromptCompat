package dev.skomlach.biometric.compat.engine.internal.behavior

import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptHost
import dev.skomlach.biometric.compat.custom.SoftwareBiometricFragmentHost
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricType

internal class BehaviorPromptDelegate(
    private val host: SoftwareBiometricPromptHost
) : SoftwareBiometricPromptDelegate {
    private val controller = host.rootView?.let { rootView ->
        BehaviorCaptureController(
            rootView = rootView,
            builder = host.builder,
            enroll = host.enroll,
            onReady = { host.callbacks.onReady(host.builder.getExtras()) }
        )
    }

    override fun shouldInstall(): Boolean =
        shouldInstallBehaviorPrompt(
            authMode = host.builder.getBehaviorAuthMode(),
            hasController = controller != null
        )

    override fun install() {
        val capture = controller ?: return
        val fragmentHost = host.callbacks as? SoftwareBiometricFragmentHost
        if (fragmentHost == null || !fragmentHost.mountContent(FRAGMENT_TAG,
                BehaviorCaptureFragment().also { it.controller = capture })) {
            host.callbacks.onFailure(AuthenticationResult(BiometricType.BIOMETRIC_BEHAVIOR,
                reason = AuthenticationFailureReason.INTERNAL_ERROR))
        }
    }

    override fun cancel() {
        dispose()
    }

    override fun dispose() {
        controller?.dispose()
        (host.callbacks as? SoftwareBiometricFragmentHost)?.unmountContent(FRAGMENT_TAG)
    }

    override fun isReadyToStartAuth(): Boolean {
        return isBehaviorPromptReady(
            authMode = host.builder.getBehaviorAuthMode(),
            hasController = controller != null,
            hasPreparedPayload = controller?.consumePrepared() == true
        )
    }

    private companion object {
        const val FRAGMENT_TAG = "behavior.capture.content"
    }
}
