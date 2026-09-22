package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import org.junit.Assert.*
import org.junit.Test

class ZkFingerCaptureSessionTest {
    @Test fun canceledNativeResultCannotCommitEnrollmentOrLockout() {
        val session = ZkFingerCaptureSession { it() }
        val enteredNative = java.util.concurrent.CountDownLatch(1)
        val finishNative = java.util.concurrent.CountDownLatch(1)
        val writes = mutableListOf<String>()
        val worker = Thread {
            session.post {
                enteredNative.countDown()
                check(finishNative.await(5, java.util.concurrent.TimeUnit.SECONDS))
                session.commit { writes += "template"; writes += "lockout" }
            }
        }
        worker.start()
        try {
            assertTrue(enteredNative.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(session.invalidate())
        } finally { finishNative.countDown() }
        worker.join(5_000)
        assertFalse(worker.isAlive)
        assertTrue(writes.isEmpty())
    }

    @Test fun activeCommitReturnsItsResultAndCancellationRejectsLaterCommits() {
        val session = ZkFingerCaptureSession { it() }
        assertEquals("saved", session.commit { "saved" })
        session.invalidate()
        assertNull(session.commit { error("must not mutate retired session") })
    }

    @Test
    fun `registered vendor callbacks stay bound when the manager replaces its session`() {
        val queue = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        var current = ZkFingerCaptureSession { queue.addLast(it) }
        val extracted = current.bindTemplate { events += "old template" }
        val error = current.bind<Int> { events += "old error" }
        val permission = current.bind<String> { events += "old permission" }
        current.invalidate()
        current = ZkFingerCaptureSession { queue.addLast(it) }
        extracted(byteArrayOf(1))
        error(42)
        permission("device")
        current.bindTemplate { events += "new template" }(byteArrayOf(2))
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(listOf("new template"), events)
    }

    @Test
    fun `vendor buffer is copied before the callback returns`() {
        val queue = ArrayDeque<() -> Unit>()
        val session = ZkFingerCaptureSession { queue.addLast(it) }
        val buffer = byteArrayOf(1, 2, 3)
        var result: ByteArray? = null
        session.postTemplate(buffer) { result = it }
        buffer.fill(0)
        queue.removeFirst().invoke()
        assertArrayEquals(byteArrayOf(1, 2, 3), result)
    }

    @Test
    fun `queued templates and errors from a retired session cannot affect its replacement`() {
        val queue = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        val old = ZkFingerCaptureSession { queue.addLast(it) }
        old.postTemplate(byteArrayOf(1)) { events += "old template" }
        old.post { events += "old error" }
        assertTrue(old.invalidate())
        assertFalse(old.invalidate())
        val next = ZkFingerCaptureSession { queue.addLast(it) }
        next.postTemplate(byteArrayOf(2)) { events += "new template" }
        old.post { events += "late callback" }
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(listOf("new template"), events)
        assertTrue(next.isActive)
    }

    @Test
    fun `cancelled session never opens native resources from a queued permission grant`() {
        val queue = ArrayDeque<() -> Unit>()
        var opened = false
        val session = ZkFingerCaptureSession { queue.addLast(it) }
        session.post { opened = true }
        session.invalidate()
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertFalse(opened)
    }
}
