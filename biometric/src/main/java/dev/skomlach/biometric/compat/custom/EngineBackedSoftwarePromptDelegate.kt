package dev.skomlach.biometric.compat.custom

class EngineBackedSoftwarePromptDelegate(
    private val enroll: Boolean,
    private val callbacks: SoftwareBiometricPromptHost.Callbacks,
    private val startMessage: (Boolean) -> CharSequence?
) : SoftwareBiometricPromptDelegate {
    private var active = true
    private var started = false

    override fun start() {
        if (!active || started || !callbacks.isPromptActive()) {
            return
        }
        started = true
        startMessage(enroll)?.let(callbacks::onHelp)
        callbacks.onReady(null)
    }

    override fun cancel() {
        active = false
    }

    override fun dispose() {
        active = false
    }

    override fun isReadyToStartAuth(): Boolean = active
}
