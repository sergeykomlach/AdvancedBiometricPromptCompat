package dev.skomlach.biometric.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class BiometricInitializationDispatchTest {
    @Test
    fun discoveryRunsOnWorkerBeforeReadinessIsPostedToMain() {
        val background = ArrayDeque<Runnable>()
        val main = ArrayDeque<Runnable>()
        val events = mutableListOf<String>()

        completeBiometricInitialization(
            dispatchBackground = { background.add(it) },
            dispatchMain = { main.add(it) },
            loadSoftware = { events += "registered"; events += "cache-invalidated" },
            onReady = { events += "ready" }
        )

        assertTrue("Discovery must not execute on the caller", events.isEmpty())
        assertTrue("No main-thread load or premature readiness", main.isEmpty())
        assertEquals(1, background.size)
        background.removeFirst().run()
        assertEquals(listOf("registered", "cache-invalidated"), events)
        assertEquals(1, main.size)
        main.removeFirst().run()
        assertEquals(listOf("registered", "cache-invalidated", "ready"), events)
        assertTrue(background.isEmpty())
        assertTrue(main.isEmpty())
    }

    @Test
    fun blockedProviderDiscoveryLeavesMainQueueResponsive() {
        val worker = Executors.newSingleThreadExecutor()
        val main = LinkedBlockingQueue<Runnable>()
        val callerThread = Thread.currentThread()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var ready = false
        try {
            completeBiometricInitialization(
                dispatchBackground = worker::execute,
                dispatchMain = { main.add(it) },
                loadSoftware = {
                    assertNotSame(callerThread, Thread.currentThread())
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS)) { "Test did not release the provider" }
                },
                onReady = { ready = true }
            )
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            var inputHandled = false
            main.add(Runnable { inputHandled = true })
            main.remove().run()
            assertTrue(inputHandled)
            assertFalse("A blocked loader must not publish readiness", ready)
            assertTrue(main.isEmpty())

            release.countDown()
            val completion = main.poll(5, TimeUnit.SECONDS)
                ?: error("Readiness was not dispatched after provider completion")
            completion.run()
            assertTrue(ready)
        } finally {
            release.countDown()
            worker.shutdownNow()
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun noInstalledSoftwareProvidersStillCompletesOnMain() {
        val background = ArrayDeque<Runnable>()
        val main = ArrayDeque<Runnable>()
        var ready = false
        completeBiometricInitialization(
            { background.add(it) }, { main.add(it) }, {}, { ready = true }
        )
        background.removeFirst().run()
        assertFalse(ready)
        main.removeFirst().run()
        assertTrue(ready)
    }

    @Test
    fun unexpectedLoadFailureDoesNotPublishPartialReadiness() {
        val background = ArrayDeque<Runnable>()
        val main = ArrayDeque<Runnable>()
        val failure = IllegalStateException("provider loading failed")
        completeBiometricInitialization(
            { background.add(it) }, { main.add(it) }, { throw failure }, { fail("not ready") }
        )
        try {
            background.removeFirst().run()
            fail("Loader failure must reach the executor's error boundary")
        } catch (actual: IllegalStateException) {
            assertTrue(actual === failure)
        }
        assertTrue(main.isEmpty())
    }
}
