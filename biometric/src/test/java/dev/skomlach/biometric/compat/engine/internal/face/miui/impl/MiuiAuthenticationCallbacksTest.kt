package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

import java.util.ArrayDeque
import org.junit.Assert.*
import org.junit.Test

class MiuiAuthenticationCallbacksTest {
    private class Recorder : IMiuiFaceManager.AuthenticationCallback() {
        val events = mutableListOf<String>()
        override fun onAuthenticationSucceeded(face: Miuiface?) { events += "success" }
        override fun onAuthenticationFailed() { events += "failed" }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) { events += "error" }
        override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) { events += "help" }
    }

    private class Fixture {
        val callbacks = MiuiAuthenticationCallbacks()
        val queueA = ArrayDeque<() -> Unit>()
        val queueB = ArrayDeque<() -> Unit>()
        val a = Recorder()
        val b = Recorder()
        fun beginA() = callbacks.begin(a) { queueA.add(it) }
        fun beginB() = callbacks.begin(b) { queueB.add(it) }
        fun drain() {
            for (queue in listOf(queueA, queueB)) while (queue.isNotEmpty()) queue.removeFirst().invoke()
        }
    }

    @Test fun queuedAThenCancelAndBeginBCannotCompleteB() {
        val f = Fixture()
        val a = f.beginA()
        a.succeeded(null)
        assertTrue(a.cancel())
        val b = f.beginB()
        f.drain()
        assertTrue(f.a.events.isEmpty())
        assertTrue(f.b.events.isEmpty())
        assertTrue(b.isActive)
        b.succeeded(null)
        b.succeeded(null)
        f.drain()
        assertEquals(listOf("success"), f.b.events)
    }

    @Test fun retiredReceiverAndLateCancelCannotAffectB() {
        val f = Fixture()
        val a = f.beginA()
        val b = f.beginB()
        a.succeeded(null)
        a.help(1, "synthetic")
        a.error(2, "synthetic")
        a.failed()
        assertFalse(a.cancel())
        assertTrue(f.queueA.isEmpty())
        assertTrue(b.isActive)
        b.succeeded(null)
        f.drain()
        assertTrue(f.a.events.isEmpty())
        assertEquals(listOf("success"), f.b.events)
    }

    @Test fun currentCallbacksUseCapturedQueueAndPreserveNonterminalEvents() {
        val f = Fixture()
        val b = f.beginB()
        b.help(1, "synthetic")
        b.failed()
        b.succeeded(null)
        assertTrue(f.b.events.isEmpty())
        assertTrue(f.queueA.isEmpty())
        f.drain()
        assertEquals(listOf("help", "failed", "success"), f.b.events)
    }

    @Test fun enrollmentOrRemovalHandoffRevokesQueuedAuthEvents() {
        val f = Fixture()
        val a = f.beginA()
        a.succeeded(null)
        f.callbacks.cancel()
        f.drain()
        assertTrue(f.a.events.isEmpty())
        assertFalse(a.isActive)
    }
}
