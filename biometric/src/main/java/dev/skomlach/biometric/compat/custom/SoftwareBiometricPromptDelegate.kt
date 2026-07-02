package dev.skomlach.biometric.compat.custom

interface SoftwareBiometricPromptDelegate {
    fun shouldInstall(): Boolean = false

    fun install() {}

    fun start() {}

    fun cancel() {}

    fun dispose() {}

    fun isReadyToStartAuth(): Boolean = true
}
