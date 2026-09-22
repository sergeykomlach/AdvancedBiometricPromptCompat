package dev.skomlach.biometric.compat.custom

import org.junit.Assert.*
import org.junit.Test

class SoftwareBiometricWorkerCallbackTest {
    @Test fun providerRevocationImmediatelyBlocksCommitsAndDeliversCancellationOnce() {
        val queue = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        val session = SoftwareBiometricWorkSession()
        val callback = SoftwareBiometricWorkerCallback(session, object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) { events += "success" }
            override fun onAuthenticationCancelled() { events += "canceled" }
        }, onTerminal = { events += "release" }, enqueue = { queue.addLast(it) })
        callback.onAuthenticationSucceeded(null)
        callback.onAuthenticationCancelled()
        callback.onAuthenticationCancelled()
        assertNull(session.runIfActive { fail("commit after remove"); Unit })
        assertTrue(events.isEmpty()) // Application notification belongs to the destination thread.
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(listOf("release", "canceled"), events)
    }

    @Test fun cancellationCannotFollowAnAlreadyDeliveredSuccess() {
        val events = mutableListOf<String>()
        val callback = SoftwareBiometricWorkerCallback(SoftwareBiometricWorkSession(), object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) { events += "success" }
            override fun onAuthenticationCancelled() { events += "canceled" }
        }) { it() }
        callback.onAuthenticationSucceeded(null)
        callback.onAuthenticationCancelled()
        assertEquals(listOf("success"), events)
    }

    @Test fun cancellationAfterPostingDropsOldHelpAndSuccessWithoutAffectingNextAttempt() {
        val queue = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        val delegate = object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
            override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) { events += "help" }
            override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) { events += "success" }
        }
        val old = SoftwareBiometricWorkSession()
        val callback = SoftwareBiometricWorkerCallback(old, delegate) { queue.addLast(it) }
        callback.onAuthenticationHelp(0, "accepted")
        callback.onAuthenticationSucceeded(null)
        old.cancel()
        val next = SoftwareBiometricWorkSession()
        SoftwareBiometricWorkerCallback(next, delegate) { queue.addLast(it) }.onAuthenticationSucceeded(null)
        assertTrue(events.isEmpty())
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(listOf("success"), events)
    }

    @Test fun onlyOneQueuedTerminalResultIsDelivered() {
        val queue = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        val session = SoftwareBiometricWorkSession()
        val callback = SoftwareBiometricWorkerCallback(session, object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) {
                assertFalse(session.isActive)
                events += "success"
            }
            override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) { events += "error" }
        }) { queue.addLast(it) }
        callback.onAuthenticationSucceeded(null)
        callback.onAuthenticationError(1, "late error")
        callback.onAuthenticationSucceeded(null)
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(listOf("success"), events)
    }
}
