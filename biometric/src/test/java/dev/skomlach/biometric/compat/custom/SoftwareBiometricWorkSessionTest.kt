package dev.skomlach.biometric.compat.custom

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SoftwareBiometricWorkSessionTest {
    @Test fun workerReturnsImmediatelyAndSerializesInference() {
        val worker = newSoftwareBiometricWorker("biometric-test")
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val caller = Thread.currentThread()
        worker.execute {
            if (Thread.currentThread() !== caller) events += "background"
            entered.countDown()
            check(resume.await(5, TimeUnit.SECONDS))
            events += "first"
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        worker.execute { events += "second"; finished.countDown() }
        assertEquals(listOf("background"), events.toList())
        resume.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("background", "first", "second"), events.toList())
    }

    @Test fun cancellationDuringInferencePreventsCommitAndCompletion() {
        val session = SoftwareBiometricWorkSession()
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        var writes = 0
        val thread = Thread {
            entered.countDown()
            check(resume.await(5, TimeUnit.SECONDS))
            session.runIfActive { writes++ }
        }.apply { start() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertTrue(session.cancel())
        resume.countDown()
        thread.join(5000)
        assertFalse(thread.isAlive)
        assertEquals(0, writes)
        assertFalse(session.complete())
        assertTrue(SoftwareBiometricWorkSession().complete())
    }

    @Test fun cancellationWaitsForAnAdmittedCommitBeforeRollback() {
        val session = SoftwareBiometricWorkSession()
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val cancelling = CountDownLatch(1)
        var stored = false
        val writer = Thread {
            session.runIfActive {
                entered.countDown()
                check(resume.await(5, TimeUnit.SECONDS))
                stored = true
            }
        }.apply { start() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val cancel = Thread {
            cancelling.countDown()
            session.cancel()
            stored = false // EnrollmentRollbackScope executes after cancellation returns.
        }.apply { start() }
        assertTrue(cancelling.await(5, TimeUnit.SECONDS))
        resume.countDown()
        writer.join(5000)
        cancel.join(5000)
        assertFalse(writer.isAlive || cancel.isAlive)
        assertFalse(stored)
    }

    @Test fun completionIsAtMostOnceAndRetiredCallbacksDoNotEnterAnotherSession() {
        val old = SoftwareBiometricWorkSession()
        old.cancel()
        val next = SoftwareBiometricWorkSession()
        assertFalse(old.complete())
        assertTrue(next.complete())
        assertFalse(next.complete())
    }
}
