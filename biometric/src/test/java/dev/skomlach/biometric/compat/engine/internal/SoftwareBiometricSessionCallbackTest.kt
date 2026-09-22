package dev.skomlach.biometric.compat.engine.internal

import dev.skomlach.biometric.compat.custom.SoftwareBiometricSessionGuard
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkSession
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkerCallback
import org.junit.Assert.*
import org.junit.Test

class SoftwareBiometricSessionCallbackTest {
    @Test fun workerRevocationReachesCoreWithoutAnExternalCancellationSignal() {
        val sessions = SoftwareBiometricSessionGuard()
        val token = sessions.start()
        val work = SoftwareBiometricWorkSession()
        val queue = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        val moduleCallback = object : SoftwareBiometricSessionCallback(
            SoftwareBiometricCallbackGate(sessions, token, { false }, { 0L }),
            { events += "clear-timeout"; events += "cancel-module"; events += "canceled" }
        ) {}
        val workerCallback = SoftwareBiometricWorkerCallback(work, moduleCallback) { queue.addLast(it) }
        workerCallback.onAuthenticationSucceeded(null)
        workerCallback.onAuthenticationCancelled()
        workerCallback.onAuthenticationCancelled()
        assertFalse(work.isActive)
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(listOf("clear-timeout", "cancel-module", "canceled"), events)
        assertFalse(sessions.isActive(token))
    }

    @Test fun providerCancellationClosesModuleBeforeReportingExactlyOnce() {
        val sessions = SoftwareBiometricSessionGuard()
        val token = sessions.start()
        val events = mutableListOf<String>()
        val gate = SoftwareBiometricCallbackGate(sessions, token, { false }, { 0L })
        val callback = object : SoftwareBiometricSessionCallback(gate, {
            assertFalse(sessions.isActive(token))
            events += "clear-timeout"
            events += "stop-module"
            events += "canceled"
        }) {}
        // Same callback entry used by ZK remove/replacement; no external cancel signal.
        callback.onAuthenticationCancelled()
        callback.onAuthenticationCancelled()
        assertEquals(listOf("clear-timeout", "stop-module", "canceled"), events)
        assertFalse(gate.dispatch { fail("late provider result") })
    }

    @Test fun retiredProviderCancellationCannotCloseReplacementModule() {
        val sessions = SoftwareBiometricSessionGuard()
        val gate = SoftwareBiometricCallbackGate(sessions, sessions.start(), { false }, { 0L })
        var closed = false
        val callback = object : SoftwareBiometricSessionCallback(gate, { closed = true }) {}
        val replacement = sessions.start()
        callback.onAuthenticationCancelled()
        assertFalse(closed)
        assertTrue(sessions.isActive(replacement))
    }

    @Test fun externallyCanceledAttemptDoesNotDispatchAnotherTerminalResult() {
        val sessions = SoftwareBiometricSessionGuard()
        val gate = SoftwareBiometricCallbackGate(sessions, sessions.start(), { true }, { 0L })
        val callback = object : SoftwareBiometricSessionCallback(gate, { fail("duplicate cancellation") }) {}
        callback.onAuthenticationCancelled()
    }
}
