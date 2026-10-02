package dev.skomlach.common.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.CoroutineContext

class NetworkReachabilityTest {
    @Test(timeout = 5_000)
    fun timeoutAbortsTheProbeWithoutChangingOsValidation() = withHarness {
        val probe = WaitingProbe()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { check(probe, timeout = 300) }
        probe.awaitStart()
        assertEquals(ReachabilityStatus.TIMED_OUT, pending.await().status)
        assertEquals(1, probe.cancelled.get())
        assertTrue(states.value.isConnected)
    }

    @Test(timeout = 5_000)
    fun cancellingTheLastWaiterAbortsTheUnderlyingRequest() = withHarness {
        val probe = WaitingProbe()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { check(probe) }
        probe.awaitStart()
        pending.cancelAndJoin()
        assertEquals(1, probe.cancelled.get())
    }

    @Test(timeout = 5_000)
    fun deadlineReturnsEvenWhenTheWorkerExecutorIsOccupied() = runBlocking {
        val releaseWorker = CountDownLatch(1)
        val workerStarted = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task).apply { isDaemon = true } }
        val dispatcher = executor.asCoroutineDispatcher()
        val workerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val states = MutableStateFlow(NetworkState(NetworkAvailability.VALIDATED, 1, generation = 1))
        val checker = NetworkReachability(states, { states.value }, workerScope) { 1_000L }
        val probe = ImmediateProbe(ProbeResponse.Http(204))
        executor.execute { workerStarted.countDown(); releaseWorker.await() }
        assertTrue(workerStarted.await(2, TimeUnit.SECONDS))
        val pending = async(Dispatchers.Default) { checker.check("https://example.com/", probe, 100, 0) }
        try {
            assertEquals(ReachabilityStatus.TIMED_OUT, withTimeout(1_000) { pending.await() }.status)
            assertEquals(0, probe.starts.get())
        } finally {
            releaseWorker.countDown()
            pending.cancelAndJoin()
            workerScope.cancel()
            dispatcher.close()
        }
    }

    @Test(timeout = 5_000)
    fun cancellationBeforeHandleInstallationCancelsTheLateHandle() = withHarness {
        val entered = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val probe = EndpointProbe { _, _, _ ->
            entered.countDown()
            releaseStart.await()
            ProbeCancellation { cancelled.countDown() }
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { check(probe) }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            withTimeout(1_000) { pending.cancelAndJoin() }
        } finally { releaseStart.countDown() }
        assertTrue(cancelled.await(2, TimeUnit.SECONDS))
    }

    @Test(timeout = 5_000)
    fun sharedProbeSurvivesOneWaiterCancellation() = withHarness {
        val probe = WaitingProbe()
        val first = async(start = CoroutineStart.UNDISPATCHED) { check(probe) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { check(probe) }
        probe.awaitStart()
        assertEquals(1, probe.starts.get())
        first.cancelAndJoin()
        assertEquals(0, probe.cancelled.get())
        probe.complete(ProbeResponse.Http(204))
        assertEquals(ReachabilityStatus.REACHABLE, second.await().status)
    }

    @Test(timeout = 5_000)
    fun routeChangeAbortsProbeAndLateSuccessCannotPopulateCache() = withHarness {
        val probe = WaitingProbe()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { check(probe) }
        probe.awaitStart()
        states.value = states.value.copy(networkId = 2, isVpn = true, generation = 2)
        assertEquals(ReachabilityStatus.NETWORK_CHANGED, pending.await().status)
        assertEquals(1, probe.cancelled.get())
        probe.complete(ProbeResponse.Http(204))
        val replacement = async(start = CoroutineStart.UNDISPATCHED) { check(probe) }
        withTimeout(1_500) { while (probe.starts.get() < 2) yield() }
        probe.complete(ProbeResponse.Http(503), index = 1)
        assertEquals(503, replacement.await().httpStatusCode)
        assertEquals(2, probe.starts.get())
    }

    @Test(timeout = 5_000)
    fun duplicateCompletionDoesNotResumeTheWaiterTwice() = withHarness {
        val probe = WaitingProbe()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { check(probe) }
        probe.awaitStart()
        probe.complete(ProbeResponse.Http(204))
        probe.complete(ProbeResponse.Http(500))
        assertEquals(204, pending.await().httpStatusCode)
    }

    @Test(timeout = 5_000)
    fun anUnvalidatedVpnCanStillReachTheConfiguredEndpoint() = withHarness {
        states.value = states.value.copy(availability = NetworkAvailability.UNVALIDATED, isVpn = true)
        val result = check(ImmediateProbe(ProbeResponse.Http(204)))
        assertEquals(ReachabilityStatus.REACHABLE, result.status)
        assertFalse(result.network.isConnected)
        assertTrue(result.network.isVpn)
    }

    @Test(timeout = 5_000)
    fun unavailableUnknownAndBlockedStatesDoNotStartRequests() = withHarness {
        val probe = ImmediateProbe(ProbeResponse.Http(204))
        for ((availability, expected) in listOf(
            NetworkAvailability.UNAVAILABLE to ReachabilityStatus.NO_NETWORK,
            NetworkAvailability.UNKNOWN to ReachabilityStatus.UNKNOWN,
            NetworkAvailability.BLOCKED to ReachabilityStatus.BLOCKED,
            NetworkAvailability.SUSPENDED to ReachabilityStatus.SUSPENDED
        )) {
            states.value = states.value.copy(availability = availability)
            assertEquals(expected, check(probe).status)
        }
        assertEquals(0, probe.starts.get())
    }

    @Test(timeout = 5_000)
    fun successfulProbeIsCachedOnlyForItsRouteAndShortTtl() = withHarness {
        val probe = ImmediateProbe(ProbeResponse.Http(204))
        assertEquals(204, check(probe, ttl = 100).httpStatusCode)
        assertEquals(204, check(probe, ttl = 100).httpStatusCode)
        assertEquals(1, probe.starts.get())
        clock.addAndGet(101)
        check(probe, ttl = 100)
        assertEquals(2, probe.starts.get())
        states.value = states.value.copy(generation = 2)
        check(probe, ttl = 100)
        assertEquals(3, probe.starts.get())
    }

    @Test(timeout = 5_000)
    fun cacheDoesNotCrossEndpointsOrClientTlsPolicies() = withHarness {
        val first = ImmediateProbe(ProbeResponse.Http(204))
        val second = ImmediateProbe(ProbeResponse.Http(204))
        check(first)
        check(first, endpoint = "https://other.example/health")
        check(second)
        assertEquals(2, first.starts.get())
        assertEquals(1, second.starts.get())
    }

    @Test(timeout = 5_000)
    fun authorizationAndServerErrorsRemainReachableWithTheirHttpStatus() = withHarness {
        for (status in listOf(401, 403, 503)) {
            val result = check(ImmediateProbe(ProbeResponse.Http(status)))
            assertEquals(ReachabilityStatus.REACHABLE, result.status)
            assertEquals(status, result.httpStatusCode)
        }
    }

    @Test(timeout = 5_000)
    fun tlsAndTransportFailuresAreDistinctAndAreNotCached() = withHarness {
        val tls = ImmediateProbe(ProbeResponse.TlsError)
        val offline = ImmediateProbe(ProbeResponse.Unreachable)
        repeat(2) {
            assertEquals(ReachabilityStatus.TLS_ERROR, check(tls).status)
            assertEquals(ReachabilityStatus.UNREACHABLE, check(offline).status)
        }
        assertEquals(2, tls.starts.get())
        assertEquals(2, offline.starts.get())
    }

    @Test(timeout = 5_000)
    fun probeRejectsUnsafeEndpointsAndUnboundedTimeouts() = withHarness {
        val probe = ImmediateProbe(ProbeResponse.Http(204))
        for (endpoint in listOf("file:///tmp/probe", "http://example.com/", "https://user:pass@example.com/", "https://example.com/#secret")) {
            expectInvalid { check(probe, endpoint = endpoint) }
        }
        expectInvalid { check(probe, timeout = 0) }
        expectInvalid { check(probe, timeout = 60_001) }
        expectInvalid { check(probe, ttl = -1) }
        assertEquals(0, probe.starts.get())
    }

    @Test(timeout = 5_000)
    fun localOnlyAndCaptiveRoutesMayReachTheApplicationsEndpoint() = withHarness {
        for (availability in listOf(NetworkAvailability.LOCAL_ONLY, NetworkAvailability.CAPTIVE_PORTAL)) {
            states.value = NetworkState(availability, 1, generation = states.value.generation + 1)
            val result = check(ImmediateProbe(ProbeResponse.Http(204)))
            assertEquals(ReachabilityStatus.REACHABLE, result.status)
            assertTrue(result.network.hasNetworkTransport)
            assertFalse(result.network.isConnected)
        }
    }

    @Test(timeout = 5_000)
    fun completedFlightIsNotReusedWhenItsPreviousCallerHasNotResumed() = withHarness {
        val dispatcher = QueueDispatcher()
        val probe = ImmediateProbe(ProbeResponse.Http(204))
        val first = async(dispatcher, start = CoroutineStart.UNDISPATCHED) { check(probe, ttl = 0) }
        try {
            assertTrue(dispatcher.queued.await(2, TimeUnit.SECONDS))
            val second = check(probe, ttl = 0)
            assertEquals(2, probe.starts.get())
            assertEquals(204, second.httpStatusCode)
        } finally { dispatcher.release() }
        assertEquals(204, first.await().httpStatusCode)
    }

    @Test(timeout = 5_000)
    fun pausedOlderWaiterCannotOverwriteANewerCachedResponse() = withHarness {
        val dispatcher = QueueDispatcher()
        val starts = AtomicInteger()
        val probe = EndpointProbe { _, _, complete ->
            complete(ProbeResponse.Http(if (starts.incrementAndGet() == 1) 204 else 503))
            ProbeCancellation { }
        }
        val first = async(dispatcher, start = CoroutineStart.UNDISPATCHED) { check(probe) }
        try {
            assertTrue(dispatcher.queued.await(2, TimeUnit.SECONDS))
            assertEquals(503, check(probe).httpStatusCode)
        } finally { dispatcher.release() }
        assertEquals(204, first.await().httpStatusCode)
        assertEquals(503, check(probe).httpStatusCode)
        assertEquals(2, starts.get())
    }

    @Test(timeout = 5_000)
    fun synchronousTransportSetupFailureIsReportedWithoutLosingTlsClassification() = withHarness {
        val tls = EndpointProbe { _, _, _ -> throw SSLHandshakeException("TLS setup failed") }
        val io = EndpointProbe { _, _, _ -> throw IOException("Transport setup failed") }
        assertEquals(ReachabilityStatus.TLS_ERROR, check(tls).status)
        assertEquals(ReachabilityStatus.UNREACHABLE, check(io).status)
    }

    @Test(timeout = 5_000)
    fun setupCancellationPropagatesInsteadOfBeingReportedAsOffline() = withHarness {
        val cancelled = EndpointProbe { _, _, _ -> throw CancellationException("Caller cancelled") }
        try { check(cancelled); fail("Expected cancellation") }
        catch (_: CancellationException) { }
    }

    @Test(timeout = 5_000)
    fun invalidPortsAreRejectedBeforeCallingTransport() = withHarness {
        val probe = ImmediateProbe(ProbeResponse.Http(204))
        expectInvalid { check(probe, endpoint = "https://example.com:0/") }
        expectInvalid { check(probe, endpoint = "https://example.com:65536/") }
        assertEquals(0, probe.starts.get())
    }

    private class QueueDispatcher : CoroutineDispatcher() {
        private val queue = ConcurrentLinkedQueue<Runnable>()
        @Volatile private var released = false
        val queued = CountDownLatch(1)
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.add(block)
            queued.countDown()
            if (released) drain()
        }
        fun drain() { while (true) (queue.poll() ?: return).run() }
        fun release() { released = true; drain() }
    }

    private class WaitingProbe : EndpointProbe {
        val starts = AtomicInteger()
        val cancelled = AtomicInteger()
        private val started = CountDownLatch(1)
        private val callbacks = CopyOnWriteArrayList<(ProbeResponse) -> Unit>()
        override fun start(endpoint: String, network: NetworkState, complete: (ProbeResponse) -> Unit): ProbeCancellation {
            callbacks.add(complete)
            starts.incrementAndGet()
            started.countDown()
            return ProbeCancellation { cancelled.incrementAndGet() }
        }
        fun awaitStart() { assertTrue("Probe did not start", started.await(2, TimeUnit.SECONDS)) }
        fun complete(response: ProbeResponse, index: Int = 0) { callbacks[index](response) }
    }

    private class ImmediateProbe(private val response: ProbeResponse) : EndpointProbe {
        val starts = AtomicInteger()
        override fun start(endpoint: String, network: NetworkState, complete: (ProbeResponse) -> Unit): ProbeCancellation {
            starts.incrementAndGet()
            complete(response)
            return ProbeCancellation { }
        }
    }

    private class Harness(val callerScope: CoroutineScope) : CoroutineScope by callerScope {
        val states = MutableStateFlow(NetworkState(NetworkAvailability.VALIDATED, 1, generation = 1))
        val clock = AtomicLong(1_000)
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val checker = NetworkReachability(states, { states.value }, workerScope, clock::get)
        suspend fun check(
            probe: EndpointProbe, endpoint: String = "https://example.com/health",
            timeout: Long = 1_500, ttl: Long = 10_000
        ): ReachabilityResult = checker.check(endpoint, probe, timeout, ttl)
    }

    private fun withHarness(block: suspend Harness.() -> Unit) = runBlocking {
        val harness = Harness(this)
        try { harness.block() } finally { harness.workerScope.cancel() }
    }

    private suspend fun expectInvalid(block: suspend () -> Unit) {
        try { block(); fail("Expected IllegalArgumentException") }
        catch (_: IllegalArgumentException) { }
    }
}
