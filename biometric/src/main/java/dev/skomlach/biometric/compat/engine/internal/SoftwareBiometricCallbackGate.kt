package dev.skomlach.biometric.compat.engine.internal

import dev.skomlach.biometric.compat.custom.SoftwareBiometricSessionGuard
import dev.skomlach.biometric.compat.custom.SoftwareBiometricSessionToken
import dev.skomlach.biometric.compat.custom.SoftwareBiometricTerminalState

/** Shared by software providers; hints are advisory and never suppress terminal results. */
internal class SoftwareBiometricCallbackGate(
    private val sessions: SoftwareBiometricSessionGuard,
    private val token: SoftwareBiometricSessionToken,
    private val canceled: () -> Boolean,
    private val elapsedRealtime: () -> Long
) {
    private var lastMessage: String? = null
    private var lastHelpAt: Long? = null

    fun canDispatch(): Boolean = sessions.isActive(token) && !canceled()

    fun terminate(state: SoftwareBiometricTerminalState, action: () -> Unit): Boolean {
        if (!canDispatch() || !sessions.tryTerminate(token, state)) return false
        action()
        return true
    }

    /** Check at execution time, not when a callback or delayed retry was queued. */
    fun dispatch(action: () -> Unit): Boolean {
        if (!canDispatch()) return false
        action()
        return true
    }

    @Synchronized
    fun tryHelp(message: CharSequence?): Boolean {
        if (!canDispatch()) return false
        val text = message?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val now = elapsedRealtime()
        lastHelpAt?.let {
            val interval = if (text == lastMessage) 2_000L else 250L
            if (now - it < interval) return false
        }
        lastMessage = text
        lastHelpAt = now
        return true
    }
}
