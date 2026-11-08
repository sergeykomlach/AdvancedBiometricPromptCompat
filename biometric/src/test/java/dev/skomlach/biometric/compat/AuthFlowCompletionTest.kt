package dev.skomlach.biometric.compat

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class AuthFlowCompletionTest {
    @Test fun terminalCallbackCanStartNextFlowAfterCleanup() {
        val queue = mutableListOf<() -> Unit>()
        val calls = mutableListOf<String>()
        val gate = AtomicBoolean(true)
        val completion = AuthFlowCompletion(
            post = { queue += it }, ownsFlow = { gate.get() },
            cleanup = { calls += "cleanup" },
            release = { calls += "release"; gate.compareAndSet(true, false) },
            onClosed = { calls += "closed" }
        )
        completion.finish {
            calls += "success"
            assertTrue(gate.tryStartAuthFlow())
        }
        completion.finish()
        completion.finish { fail("duplicate result") }
        assertEquals(1, queue.size)
        queue.removeAt(0).invoke()
        assertEquals(listOf("cleanup", "release", "success", "closed"), calls)
        assertTrue(gate.get())
    }

    @Test fun failureBeforeUiOpenedStillReleasesFlowExactlyOnce() {
        val queue = mutableListOf<() -> Unit>()
        var released = 0
        var failed = 0
        val completion = AuthFlowCompletion({ queue += it }, { true }, {}, { released++; true }, {})
        completion.finish { failed++ }
        queue.removeAt(0).invoke()
        completion.finish { failed++ }
        assertEquals(1, released)
        assertEquals(1, failed)
        assertTrue(queue.isEmpty())
    }

    @Test fun oldFlowCannotCleanUpOrNotifyAfterReplacement() {
        val queue = mutableListOf<() -> Unit>()
        val completion = AuthFlowCompletion({ queue += it }, { false }, { fail("stale cleanup") }, { fail("stale release"); false }, { fail("stale close") })
        completion.finish { fail("stale terminal") }
        queue.single().invoke()
    }

    @Test fun closeCallbackCanStartNextFlowWithoutATerminalEvent() {
        val queue = mutableListOf<() -> Unit>()
        val gate = AtomicBoolean(true)
        val completion = AuthFlowCompletion({ queue += it }, { gate.get() }, {}, { gate.compareAndSet(true, false) }, { assertTrue(gate.tryStartAuthFlow()) })
        completion.finish()
        queue.single().invoke()
        assertTrue(gate.get())
    }
    @Test fun callbacksTriggeredByCleanupCannotOverrideTheTerminalResult() {
        val queue = mutableListOf<() -> Unit>()
        val events = mutableListOf<String>()
        lateinit var completion: AuthFlowCompletion
        completion = AuthFlowCompletion(
            post = { queue += it }, ownsFlow = { true },
            cleanup = { completion.finish { fail("cleanup cancellation replaced success") } },
            release = { true }, onClosed = { events += "closed" }
        )
        completion.finish { events += "success" }
        queue.single().invoke()
        assertEquals(listOf("success", "closed"), events)
    }

    @Test fun enrollmentRollbackKeepsGateUntilCleanupCompletes() {
        val queue = mutableListOf<() -> Unit>()
        val events = mutableListOf<String>()
        val completion = AuthFlowCompletion(
            post = { queue += it }, ownsFlow = { true },
            cleanup = { events += "ui cleanup" },
            release = { events += "released"; true },
            onClosed = { events += "closed" },
            afterCleanup = { complete -> queue += { events += "rollback"; complete() } }
        )
        completion.finish { events += "cancelled" }
        queue.removeAt(0).invoke()
        assertEquals(listOf("ui cleanup"), events)
        assertTrue(completion.isFinishing())
        queue.removeAt(0).invoke()
        assertEquals(listOf("ui cleanup", "rollback", "released", "cancelled", "closed"), events)
    }
}
