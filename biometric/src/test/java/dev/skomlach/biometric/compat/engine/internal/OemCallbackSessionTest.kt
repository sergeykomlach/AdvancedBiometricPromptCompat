package dev.skomlach.biometric.compat.engine.internal

import org.junit.Assert.*
import org.junit.Test

class OemCallbackSessionTest {
    @Test fun queuedVendorEventCannotBeReboundToTheNewConsumer() {
        val sessions = OemCallbackSession<(String) -> Unit>()
        val events = mutableListOf<String>()
        val old = sessions.begin { events += "old:$it" }
        val queued = { sessions.deliver(old) { it("error") } }
        val current = sessions.begin { events += "current:$it" }
        queued()
        sessions.deliver(old) { it("cancel") }
        assertTrue(events.isEmpty())
        sessions.deliver(current) { it("error") }
        assertEquals(listOf("current:error"), events)
    }

    @Test fun reusingTheSameCallbackStillCreatesADifferentAttempt() {
        val sessions = OemCallbackSession<() -> Unit>()
        var count = 0
        val callback = { count++; Unit }
        val old = sessions.begin(callback)
        val current = sessions.begin(callback)
        sessions.deliver(old) { it() }
        sessions.deliver(current) { it() }
        assertEquals(1, count)
        sessions.clear()
        sessions.deliver(current) { it() }
        assertEquals(1, count)
    }
}
