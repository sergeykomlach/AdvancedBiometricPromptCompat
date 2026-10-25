package dev.skomlach.biometric.compat.engine.internal.face

import java.util.ArrayDeque
import org.junit.Assert.*
import org.junit.Test

class FaceAuthenticationRequestGateTest {
    private class Fixture {
        val queue = ArrayDeque<() -> Unit>()
        val delivered = mutableListOf<String>()
        val gate = FaceAuthenticationRequestGate<() -> Unit> { queue.add(it) }
        fun begin(label: String) = gate.begin { delivered += label }
        fun success(id: Int) = gate.dispatch(id, true) { it() }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().invoke() }
    }

    @Test fun queuedOldSuccessCannotCompleteNewAuthentication() {
        val f = Fixture()
        val a = f.begin("A")
        f.success(a)
        assertEquals(a, f.gate.cancel())
        val b = f.begin("B")
        assertTrue(b > a)
        f.drain()
        assertTrue(f.delivered.isEmpty())
        assertTrue(f.gate.isActive(b))
        f.success(b)
        f.success(b)
        f.drain()
        assertEquals(listOf("B"), f.delivered)
    }

    @Test fun lateOldEventAfterReplacementIsRejectedAtReceive() {
        val f = Fixture()
        val a = f.begin("A")
        val b = f.begin("B")
        f.success(a)
        assertTrue(f.queue.isEmpty())
        f.success(b)
        f.drain()
        assertEquals(listOf("B"), f.delivered)
    }

    @Test fun synchronousVendorCallbackFromCancelCannotReviveOwner() {
        val f = Fixture()
        val a = f.begin("A")
        val cancelled = f.gate.cancel()!!
        // Model the vendor callback invoked from cancelAuthenticate(cancelled).
        f.success(cancelled)
        f.drain()
        assertFalse(f.gate.isActive(a))
        assertTrue(f.delivered.isEmpty())
        assertNull(f.gate.cancel())
    }

    @Test fun terminalCallbackCanStartNewRequestWithoutBeingClearedAfterReturn() {
        val f = Fixture()
        var next = 0
        val current = f.gate.begin { next = f.begin("next") }
        f.success(current)
        f.drain()
        assertTrue(f.gate.isActive(next))
        f.success(next)
        f.drain()
        assertEquals(listOf("next"), f.delivered)
    }

    @Test fun nonterminalStatusAndFailureDoNotConsumeCurrentOwner() {
        val f = Fixture()
        val a = f.begin("A")
        f.gate.dispatch(a, false) { f.delivered += "status" }
        f.gate.dispatch(a, false) { f.delivered += "failed" }
        f.success(a)
        f.drain()
        assertEquals(listOf("status", "failed", "A"), f.delivered)
        assertFalse(f.gate.isActive(a))
    }

    @Test fun retiredInitFailureCannotReplaceTheNextOwner() {
        val f = Fixture()
        val a = f.begin("A")
        val b = f.begin("B")
        f.gate.dispatch(a, true) { f.delivered += "init failure" }
        f.drain()
        assertTrue(f.gate.isActive(b))
        assertTrue(f.delivered.isEmpty())
    }
}
