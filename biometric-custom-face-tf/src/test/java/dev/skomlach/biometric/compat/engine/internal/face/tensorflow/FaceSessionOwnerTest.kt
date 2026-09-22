package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkSession
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkerCallback
import org.junit.Assert.*
import org.junit.Test

class FaceSessionOwnerTest {
    @Test fun removalByAnotherManagerRevokesQueuedSuccessBeforeDeletingStorage() {
        val owner = FaceSessionOwner()
        val queue = ArrayDeque<() -> Unit>()
        val session = SoftwareBiometricWorkSession()
        val events = mutableListOf<String>()
        val callback = SoftwareBiometricWorkerCallback(session, object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) { events += "success" }
            override fun onAuthenticationCancelled() { events += "canceled" }
        }, onTerminal = { owner.release(session) }, enqueue = { queue.addLast(it) })
        owner.claim(session, callback::onAuthenticationCancelled)
        callback.onAuthenticationSucceeded(null)
        owner.revoke {
            assertFalse(session.isActive)
            assertFalse(owner.owns(session))
            assertNull(session.runIfActive { fail("write after removal"); Unit })
            events += "removed"
        }
        owner.revoke { }
        val replacement = SoftwareBiometricWorkSession()
        owner.claim(replacement) { replacement.cancel() }
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(listOf("removed", "canceled"), events)
        assertTrue(owner.owns(replacement))
    }

    @Test fun terminalDeliveryReleasesTheOwnerEvenWithoutAnApplicationCallback() {
        val owner = FaceSessionOwner()
        val queue = ArrayDeque<() -> Unit>()
        val session = SoftwareBiometricWorkSession()
        owner.claim(session) { session.cancel(); owner.release(session) }
        val callback = SoftwareBiometricWorkerCallback(session, null,
            onTerminal = { owner.release(session) }, enqueue = { queue.addLast(it) })
        callback.onAuthenticationSucceeded(null)
        assertTrue(owner.owns(session))
        queue.removeFirst().invoke()
        assertFalse(owner.owns(session))
    }

    @Test fun replacementCancelsAQueuedResultAfterCaptureHasStopped() {
        val owner = FaceSessionOwner()
        val queue = ArrayDeque<() -> Unit>()
        val old = SoftwareBiometricWorkSession()
        owner.claim(old) { old.cancel(); owner.release(old) }
        var successes = 0
        val callback = SoftwareBiometricWorkerCallback(old, object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) { successes++ }
        }) { queue.addLast(it) }
        callback.onAuthenticationSucceeded(null)
        val next = SoftwareBiometricWorkSession()
        owner.claim(next) { next.cancel(); owner.release(next) }
        owner.release(old) // A late camera cleanup must not release the replacement.
        assertTrue(owner.owns(next))
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(0, successes)
    }

    @Test fun cancellationBeforeTheWorkerStartsReleasesTheOwner() {
        val owner = FaceSessionOwner()
        val session = SoftwareBiometricWorkSession()
        owner.claim(session) { session.cancel(); owner.release(session) }
        session.cancel()
        owner.release(session)
        assertFalse(owner.owns(session))
    }

    @Test fun differentManagerReplacementInvalidatesRunningInferenceImmediately() {
        val owner = FaceSessionOwner()
        val first = SoftwareBiometricWorkSession()
        owner.claim(first) { first.cancel(); owner.release(first) }
        val second = SoftwareBiometricWorkSession()
        owner.claim(second) { second.cancel(); owner.release(second) }
        assertFalse(first.isActive)
        assertTrue(second.isActive)
    }
}
