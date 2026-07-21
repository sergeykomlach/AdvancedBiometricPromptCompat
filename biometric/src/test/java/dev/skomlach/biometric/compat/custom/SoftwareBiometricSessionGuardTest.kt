package dev.skomlach.biometric.compat.custom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareBiometricSessionGuardTest {

    @Test
    fun onlyFirstTerminalTransitionIsAccepted() {
        val guard = SoftwareBiometricSessionGuard()
        val session = guard.start()

        assertTrue(guard.tryTerminate(session, SoftwareBiometricTerminalState.SUCCEEDED))
        assertFalse(guard.tryTerminate(session, SoftwareBiometricTerminalState.FAILED))
        assertFalse(guard.isActive(session))
    }

    @Test
    fun cancellationRejectsLaterSuccess() {
        val guard = SoftwareBiometricSessionGuard()
        val session = guard.start()

        assertTrue(guard.tryTerminate(session, SoftwareBiometricTerminalState.CANCELLED))
        assertFalse(guard.tryTerminate(session, SoftwareBiometricTerminalState.SUCCEEDED))
    }

    @Test
    fun staleSessionCannotTerminateCurrentSession() {
        val guard = SoftwareBiometricSessionGuard()
        val stale = guard.start()
        guard.tryTerminate(stale, SoftwareBiometricTerminalState.CANCELLED)
        val current = guard.start()

        assertFalse(guard.tryTerminate(stale, SoftwareBiometricTerminalState.SUCCEEDED))
        assertTrue(guard.tryTerminate(current, SoftwareBiometricTerminalState.SUCCEEDED))
    }

    @Test
    fun timeoutIsTerminal() {
        val guard = SoftwareBiometricSessionGuard()
        val session = guard.start()

        assertTrue(guard.tryTerminate(session, SoftwareBiometricTerminalState.EXPIRED))
        assertFalse(guard.isActive(session))
    }
}
