package dev.skomlach.biometric.compat.custom

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SoftwareBiometricWorkScopeTest {
    @Test fun failedRemovalStillRevokesAllSessionsAndDeliversOutsideTheLock() {
        val scope = SoftwareBiometricWorkScope()
        val events = mutableListOf<String>()
        val first = scope.newSession()
        val second = scope.newSession()
        for ((session, name) in listOf(first to "first", second to "second")) {
            SoftwareBiometricWorkerCallback(session, object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
                override fun onAuthenticationCancelled() {
                    assertFalse(Thread.holdsLock(scope.lock))
                    assertFalse(session.isActive)
                    events += name
                }
            }) { it() }
        }
        val error = IllegalStateException("storage unavailable")
        assertSame(error, assertThrows(IllegalStateException::class.java) {
            scope.revoke { throw error }
        })
        scope.revoke {}
        assertEquals(listOf("first", "second"), events)
        assertNull(first.runIfActive { "write" })
        val replacement = scope.newSession()
        assertTrue(replacement.isActive)
        replacement.complete()
    }

    @Test fun oneRejectedNotificationDoesNotLeaveAnotherSessionUnnotified() {
        val scope = SoftwareBiometricWorkScope()
        val first = scope.newSession()
        val second = scope.newSession()
        SoftwareBiometricWorkerCallback(first, null) { error("delivery rejected") }
        var cancelled = 0
        SoftwareBiometricWorkerCallback(second, object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
            override fun onAuthenticationCancelled() { cancelled++ }
        }) { it() }
        assertThrows(IllegalStateException::class.java) { scope.revoke {} }
        assertEquals(1, cancelled)
        assertFalse(first.isActive)
        assertFalse(second.isActive)
    }

    @Test fun completedAndCancelledSessionsDoNotReceiveLaterRevocation() {
        val scope = SoftwareBiometricWorkScope()
        val completed = scope.newSession()
        val cancelled = scope.newSession()
        val events = mutableListOf<String>()
        fun callback(session: SoftwareBiometricWorkSession) =
            SoftwareBiometricWorkerCallback(session, object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) { events += "success" }
                override fun onAuthenticationCancelled() { events += "cancel" }
            }) { it() }
        callback(completed).onAuthenticationSucceeded(null)
        callback(cancelled).onAuthenticationCancelled()
        scope.revoke {}
        assertEquals(listOf("success", "cancel"), events)
    }

    @Test fun removalWaitsForAdmittedCommitThenBlocksEveryLaterCommit() {
        val scope = SoftwareBiometricWorkScope()
        val session = scope.newSession()
        val commitEntered = CountDownLatch(1)
        val releaseCommit = CountDownLatch(1)
        val removalStarted = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        var stored = false
        val writer = Thread {
            try {
                session.runIfActive {
                    commitEntered.countDown()
                    check(releaseCommit.await(5, TimeUnit.SECONDS))
                    stored = true
                }
            } catch (failure: Throwable) { error.set(failure) }
        }
        val remover = Thread {
            try {
                removalStarted.countDown()
                scope.revoke { stored = false }
            } catch (failure: Throwable) { error.set(failure) }
        }
        writer.start()
        try {
            assertTrue(commitEntered.await(5, TimeUnit.SECONDS))
            remover.start()
            assertTrue(removalStarted.await(5, TimeUnit.SECONDS))
        } finally {
            releaseCommit.countDown()
            writer.join(5_000)
            if (remover.state != Thread.State.NEW) remover.join(5_000)
        }
        assertFalse(writer.isAlive)
        assertFalse(remover.isAlive)
        assertNull(error.get())
        assertFalse(stored)
        assertNull(session.runIfActive { stored = true })
    }
}
