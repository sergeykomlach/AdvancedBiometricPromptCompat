package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ZkFingerOperationOwnerTest {
    @Test fun removeDuringNativeWorkRejectsLaterCommitAndResult() {
        val owner = ZkFingerOperationOwner()
        val callbacks = ArrayDeque<() -> Unit>()
        val templates = mutableSetOf("old")
        var successes = 0
        val session = owner.start { ZkFingerCaptureSession { it() } }
        val enteredNative = CountDownLatch(1)
        val finishNative = CountDownLatch(1)
        val worker = Thread {
            session.post {
                enteredNative.countDown()
                check(finishNative.await(5, TimeUnit.SECONDS))
                session.commit { templates.add("new") }
                session.postCallback({ callbacks.addLast(it) }, terminal = true) { successes++ }
            }
        }
        worker.start()
        try {
            assertTrue(enteredNative.await(5, TimeUnit.SECONDS))
            owner.revoke { templates.clear() }
        } finally { finishNative.countDown() }
        worker.join(5_000)
        assertFalse(worker.isAlive)
        while (callbacks.isNotEmpty()) callbacks.removeFirst().invoke()
        assertTrue(templates.isEmpty())
        assertEquals(0, successes)
    }

    @Test fun removeRevokesSuccessAlreadyQueuedBeforeNativeCleanup() {
        val owner = ZkFingerOperationOwner()
        val queue = ArrayDeque<() -> Unit>()
        var successes = 0
        val session = owner.start { ZkFingerCaptureSession { it() } }
        session.postCallback({ queue.addLast(it) }, terminal = true) { successes++ }
        session.stopCapture()
        owner.revoke { }
        queue.removeFirst().invoke()
        assertEquals(0, successes)
    }

    @Test fun ordinaryNativeCleanupPreservesExactlyOneTerminalDelivery() {
        val session = ZkFingerCaptureSession { it() }
        val queue = ArrayDeque<() -> Unit>()
        var terminalResults = 0
        repeat(2) {
            session.postCallback({ queue.addLast(it) }, terminal = true) { terminalResults++ }
        }
        session.stopCapture()
        assertNull(session.commit { error("capture is stopped") })
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(1, terminalResults)
        assertFalse(session.invalidate())
    }

    @Test fun removeBeforeQueuedAuthenticationStartsCannotBeUndoneByThatStart() {
        val owner = ZkFingerOperationOwner()
        val nativeQueue = ArrayDeque<() -> Unit>()
        var starts = 0
        val session = owner.start { ZkFingerCaptureSession { nativeQueue.addLast(it) } }
        session.post { starts++ }
        owner.revoke { }
        while (nativeQueue.isNotEmpty()) nativeQueue.removeFirst().invoke()
        assertEquals(0, starts)
    }

    @Test fun replacementManagerAndLateReleaseCannotRevokeNewOperation() {
        val owner = ZkFingerOperationOwner()
        var cancellations = 0
        val old = owner.start { ZkFingerCaptureSession(onInvalidated = { cancellations++ }) { it() } }
        val next = owner.start { ZkFingerCaptureSession { it() } }
        owner.release(old)
        assertFalse(old.isActive)
        assertTrue(next.isActive)
        owner.revoke { }
        assertFalse(next.isActive)
        assertEquals(1, cancellations)
    }
}
