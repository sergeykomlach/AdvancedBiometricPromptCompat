package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

import java.util.ArrayDeque
import org.junit.Assert.*
import org.junit.Test

class Miui3DAuthenticationEventsTest {
    private class Recorder : IMiuiFaceManager.AuthenticationCallback() {
        val events = mutableListOf<String>()
        override fun onAuthenticationSucceeded(face: Miuiface?) { events += "success" }
        override fun onAuthenticationFailed() { events += "timeout" }
        override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) { events += "help:$helpCode" }
    }
    private class Fixture {
        val events = Miui3DAuthenticationEvents()
        val queue = ArrayDeque<() -> Unit>()
        val a = Recorder()
        val b = Recorder()
        val stops = mutableListOf<String>()
        fun begin(label: String, callback: IMiuiFaceManager.AuthenticationCallback) =
            events.begin(callback, { queue.add(it) }, { stops += label })
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().invoke() }
    }

    @Test fun queuedOldChannelSuccessCannotCompleteNewAttempt() {
        val f = Fixture()
        val a = f.begin("A", f.a)
        a.onEvent(1, 40, 0, 0)
        assertTrue(a.cancel())
        val b = f.begin("B", f.b)
        f.drain()
        assertTrue(f.a.events.isEmpty())
        assertTrue(f.b.events.isEmpty())
        assertTrue(b.isActive)
    }

    @Test fun lateOldChannelAndTimeoutCannotAffectReplacement() {
        val f = Fixture()
        val a = f.begin("A", f.a)
        val b = f.begin("B", f.b)
        a.onEvent(1, 40, 0, 0)
        a.timeout()
        assertFalse(a.cancel())
        b.onEvent(1, 40, 0, 0)
        b.onEvent(1, 40, 0, 0)
        f.drain()
        assertTrue(f.a.events.isEmpty())
        assertEquals(listOf("success"), f.b.events)
        assertEquals(listOf("B"), f.stops)
    }

    @Test fun nonAuthEventsAndOtherModulesCannotSucceed() {
        val f = Fixture()
        val a = f.begin("A", f.a)
        a.onEvent(2, 40, 0, 0)
        a.onEvent(1, 30, 0, 0)
        a.onEvent(1, 43, 0, 0)
        f.drain()
        assertTrue(f.a.events.isEmpty())
        assertTrue(a.isActive)
    }

    @Test fun timeoutIsTerminalAndClosesCaptureBeforeCallback() {
        val f = Fixture()
        val a = f.begin("A", f.a)
        a.timeout()
        a.onEvent(1, 40, 0, 0)
        f.drain()
        assertEquals(listOf("timeout"), f.a.events)
        assertEquals(listOf("A"), f.stops)
        assertFalse(a.isActive)
    }

    @Test fun successCleanupPrecedesReentrantAuthentication() {
        val f = Fixture()
        lateinit var b: Miui3DAuthenticationEvents.Attempt
        val callback = object : IMiuiFaceManager.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(face: Miuiface?) {
                assertEquals(listOf("A"), f.stops)
                b = f.begin("B", f.b)
            }
        }
        val a = f.begin("A", callback)
        a.onEvent(1, 40, 0, 0)
        f.drain()
        assertFalse(a.cancel())
        assertTrue(b.isActive)
        b.onEvent(1, 40, 0, 0)
        f.drain()
        assertEquals(listOf("success"), f.b.events)
        assertEquals(listOf("A", "B"), f.stops)
    }

    @Test fun currentChannelPreservesHelpAndSuccess() {
        val f = Fixture()
        val b = f.begin("B", f.b)
        b.onEvent(1, 41, 0, 0)
        b.onEvent(1, 42, 12, 0)
        b.onEvent(1, 40, 0, 0)
        f.drain()
        assertEquals(listOf("help:70", "help:12", "success"), f.b.events)
    }
}
