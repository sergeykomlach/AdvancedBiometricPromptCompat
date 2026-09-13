package dev.skomlach.biometric.compat.utils.activityView

import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconStateDispatcherTest {
    private val type = BiometricType.BIOMETRIC_FINGERPRINT
    private class Listener : IconStateHelper.IconStateListener {
        val events = mutableListOf<String>()
        override fun onError(type: BiometricType?) { events += "error" }
        override fun onSuccess(type: BiometricType?) { events += "success" }
        override fun reset(type: BiometricType?) { events += "reset" }
        override fun onAvailabilityChanged() { events += "availability" }
    }
    private class Queue {
        val immediate = mutableListOf<Runnable>()
        val delayed = mutableListOf<Runnable>()
        val dispatcher = IconStateDispatcher(
            { immediate.add(it) }, { task, _ -> delayed.add(task) },
            { immediate.remove(it); delayed.remove(it) }
        )
        fun drain() { while (immediate.isNotEmpty()) immediate.removeAt(0).run() }
    }

    @Test fun repeatedErrorsKeepOnlyTheLatestReset() {
        val q = Queue(); val listener = Listener(); q.dispatcher.register(listener)
        repeat(20) { q.dispatcher.error(type); q.drain() }
        assertEquals(1, q.delayed.size)
        q.delayed.removeAt(0).run()
        assertEquals(1, listener.events.count { it == "reset" })
    }

    @Test fun successCancelsResetEvenIfItWasAlreadyDequeued() {
        val q = Queue(); val listener = Listener(); q.dispatcher.register(listener)
        q.dispatcher.error(type); q.drain()
        val obsolete = q.delayed.single()
        q.dispatcher.success(type); q.drain()
        obsolete.run()
        assertTrue(q.delayed.isEmpty())
        assertEquals(listOf("error", "success"), listener.events)
    }

    @Test fun unregisterDropsPendingEventsAndTheyDoNotReachTheNextDialog() {
        val q = Queue(); val old = Listener(); q.dispatcher.register(old)
        q.dispatcher.error(type)
        val dequeued = q.immediate.single()
        q.dispatcher.unregister(old)
        val next = Listener(); q.dispatcher.register(next)
        dequeued.run(); q.drain()
        assertTrue(old.events.isEmpty())
        assertTrue(next.events.isEmpty())
        assertTrue(q.delayed.isEmpty())
    }

    @Test fun reregisteringSameListenerDoesNotRevivePreviousTimers() {
        val q = Queue(); val listener = Listener(); q.dispatcher.register(listener)
        q.dispatcher.error(type); q.drain()
        val obsolete = q.delayed.single()
        q.dispatcher.unregister(listener); q.dispatcher.register(listener)
        obsolete.run()
        assertEquals(listOf("error"), listener.events)
        assertTrue(q.delayed.isEmpty())
    }

    @Test fun unregisteringOneListenerPreservesOtherRegistrations() {
        val q = Queue(); val first = Listener(); val second = Listener()
        q.dispatcher.register(first); q.dispatcher.register(second)
        q.dispatcher.error(type); q.drain()
        q.dispatcher.unregister(first)
        assertEquals(1, q.delayed.size)
        q.delayed.single().run()
        assertEquals(listOf("error"), first.events)
        assertEquals(listOf("error", "reset"), second.events)
    }

    @Test fun refreshesCoalesceWithoutCancelingErrorFeedback() {
        val q = Queue(); val listener = Listener(); q.dispatcher.register(listener)
        q.dispatcher.error(type); q.drain()
        repeat(10) { q.dispatcher.refreshAvailability() }
        assertEquals(1, q.immediate.size)
        q.drain()
        assertEquals(1, q.delayed.size)
        assertEquals(listOf("error", "availability"), listener.events)
    }
}
