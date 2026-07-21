package dev.skomlach.biometric.compat.custom

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

@JvmInline
value class SoftwareBiometricSessionToken internal constructor(val id: Long)

enum class SoftwareBiometricTerminalState {
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED
}

class SoftwareBiometricSessionGuard {
    private data class ActiveSession(
        val token: SoftwareBiometricSessionToken,
        val terminalState: SoftwareBiometricTerminalState? = null
    )

    private val random = SecureRandom()
    private val activeSession = AtomicReference<ActiveSession?>(null)

    fun start(): SoftwareBiometricSessionToken {
        val token = SoftwareBiometricSessionToken(random.nextLong())
        activeSession.set(ActiveSession(token))
        return token
    }

    fun isActive(token: SoftwareBiometricSessionToken): Boolean =
        activeSession.get()?.let { it.token == token && it.terminalState == null } == true

    fun tryTerminate(
        token: SoftwareBiometricSessionToken,
        terminalState: SoftwareBiometricTerminalState
    ): Boolean {
        while (true) {
            val current = activeSession.get() ?: return false
            if (current.token != token || current.terminalState != null) return false
            if (activeSession.compareAndSet(current, current.copy(terminalState = terminalState))) {
                return true
            }
        }
    }
}
