package dev.skomlach.common.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

class InternetConnectivityMonitorTest {
    @Test(timeout = 5_000)
    fun initialCheckingDoesNotNotifyAConfirmedOfflineState() = withHarness {
        val observed = observe()
        probe.awaitRequests(1)
        assertEquals(InternetAccess.CHECKING, monitor.states.value.internetAccess)
        assertFalse(monitor.read().isConnected)
        assertTrue(observed.isEmpty())
        confirmFailure()
        assertEquals(listOf(false), observed.toList())
    }

    @Test(timeout = 5_000)
    fun validatedOsRouteStillNeedsASuccessfulActiveCheck() = withHarness {
        assertTrue(raw.value.isConnected)
        assertFalse(monitor.read().isConnected)
        succeed()
        assertEquals(InternetAccess.AVAILABLE, monitor.states.value.internetAccess)
    }

    @Test(timeout = 5_000)
    fun stalledValidatedVpnIsConfirmedOfflineAfterTwoDeadlines() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val raw = MutableStateFlow(online().copy(isVpn = true))
        val cancelled = AtomicInteger()
        val monitor = InternetConnectivityMonitor(raw, { raw.value }, {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancelled.incrementAndGet() }
            }
        }, scope, clockMillis = { System.nanoTime() / 1_000_000 }, timeoutMillis = 50,
            confirmationMillis = 20, retryMillis = listOf(1_000))
        try {
            assertEquals(InternetAccess.CHECKING, monitor.states.value.internetAccess)
            monitor.start()
            await { monitor.states.value.internetAccess == InternetAccess.UNAVAILABLE }
            assertTrue(raw.value.isConnected)
            assertTrue(monitor.states.value.isVpn)
            assertTrue(cancelled.get() >= 2)
        } finally { monitor.stop(); scope.cancel() }
    }

    @Test(timeout = 5_000)
    fun firstFailedRefreshRetainsTheConfirmedOnlineVerdict() = withHarness {
        succeed()
        advanceTo(3_000, 2)
        probe.complete(false, 1)
        assertTrue(monitor.read().isConnected)
        assertEquals(InternetAccess.AVAILABLE, monitor.states.value.internetAccess)
    }

    @Test(timeout = 5_000)
    fun twoFailedRefreshesConfirmOfflineWithoutAnyOsRouteChange() = withHarness {
        succeed()
        advanceTo(3_000, 2)
        confirmFailure(1)
        assertFalse(monitor.read().isConnected)
        assertEquals(NetworkAvailability.VALIDATED, raw.value.availability)
        assertEquals(1L, raw.value.generation)
    }

    @Test(timeout = 12_000)
    fun productionIntervalsDetectASilentFreezeWithinTenSecondsWithoutConsumerCalls() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val raw = MutableStateFlow(online().copy(isVpn = true))
        val calls = AtomicInteger()
        val cancelled = AtomicInteger()
        val monitor = InternetConnectivityMonitor(raw, { raw.value }, {
            if (calls.incrementAndGet() == 1) true
            else suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancelled.incrementAndGet() }
            }
        }, scope, clockMillis = { System.nanoTime() / 1_000_000 })
        try {
            monitor.start()
            await { monitor.states.value.isConnected }
            withTimeout(10_000) {
                while (monitor.states.value.isConnected) delay(10)
            }
            assertTrue(raw.value.isConnected)
            assertTrue(calls.get() >= 3)
            assertTrue(cancelled.get() >= 2)
        } finally { monitor.stop(); scope.cancel() }
    }

    @Test(timeout = 5_000)
    fun failedWifiCellularEthernetAndVpnProduceTheSameConfirmedOfflineVerdict() = withHarness {
        for ((index, transport) in listOf(1, 0, 3, 4).withIndex()) {
            raw.value = raw.value.copy(networkId = (index + 2).toLong(), generation = (index + 2).toLong(),
                isVpn = transport == 4, transports = setOf(transport))
            monitor.read()
            val request = probe.awaitRoute(raw.value.networkId!!)
            confirmFailure(request)
            assertFalse(monitor.read().isConnected)
            assertTrue(monitor.states.value.hasNetworkTransport)
        }
    }

    @Test(timeout = 5_000)
    fun unusableRoutesAreConfirmedWithoutStartingHttpsChecks() = runBlocking {
        for (availability in listOf(NetworkAvailability.UNAVAILABLE, NetworkAvailability.BLOCKED,
            NetworkAvailability.SUSPENDED, NetworkAvailability.UNKNOWN, NetworkAvailability.LOCAL_ONLY)) {
            withHarness(initial = NetworkState(availability, if (availability == NetworkAvailability.UNAVAILABLE) null else 1)) {
                assertEquals(InternetAccess.CHECKING, monitor.states.value.internetAccess)
                clock.set(1_000)
                assertFalse(monitor.read().isConnected)
                assertEquals(InternetAccess.UNAVAILABLE, monitor.states.value.internetAccess)
                assertTrue(probe.requests.isEmpty())
            }
        }
    }

    @Test(timeout = 5_000)
    fun briefSourceLossAndHandoverDoNotPublishOfflineOrDuplicateOnline() = withHarness {
        val observed = observe()
        succeed()
        raw.value = NetworkState(NetworkAvailability.UNAVAILABLE, generation = 2)
        assertTrue(monitor.read().isConnected)
        assertFalse(monitor.states.value.hasNetworkTransport)
        clock.set(500)
        raw.value = online().copy(networkId = 2, generation = 3)
        assertTrue(monitor.read().isConnected)
        probe.awaitRequests(2)
        probe.complete(true, 1)
        assertEquals(listOf(true), observed.toList())
    }

    @Test(timeout = 5_000)
    fun persistentSourceLossConfirmsOfflineOnlyAfterTheGracePeriod() = withHarness {
        val observed = observe()
        succeed()
        raw.value = NetworkState(NetworkAvailability.UNAVAILABLE, generation = 2)
        assertTrue(monitor.read().isConnected)
        clock.set(999)
        assertTrue(monitor.read().isConnected)
        clock.set(1_000)
        assertFalse(monitor.read().isConnected)
        assertEquals(listOf(true, false), observed.toList())
    }

    @Test(timeout = 5_000)
    fun screenWakeStartsAFreshCheckButKeepsConfirmedOnline() = withHarness {
        val observed = observe()
        succeed()
        raw.value = raw.value.copy(generation = 2)
        assertTrue(monitor.read().isConnected)
        probe.awaitRequests(2)
        assertEquals(InternetAccess.AVAILABLE, monitor.states.value.internetAccess)
        probe.complete(true, 1)
        assertEquals(listOf(true), observed.toList())
    }

    @Test(timeout = 5_000)
    fun screenWakeKeepsConfirmedOfflineUntilASuccessfulProbe() = withHarness {
        val observed = observe()
        probe.awaitRequests(1)
        confirmFailure()
        raw.value = raw.value.copy(generation = 2)
        assertFalse(monitor.read().isConnected)
        probe.awaitRequests(3)
        assertEquals(listOf(false), observed.toList())
        probe.complete(true, 2)
        assertEquals(listOf(false, true), observed.toList())
    }

    @Test(timeout = 5_000)
    fun changingCheckConfigurationRetainsTheVerdictWhileRechecking() = withHarness {
        succeed()
        monitor.invalidate()
        assertTrue(monitor.read().isConnected)
        probe.awaitRequests(2)
        confirmFailure(1)
        assertFalse(monitor.read().isConnected)
        assertEquals(1L, raw.value.generation)
    }

    @Test(timeout = 5_000)
    fun delayedWatchdogRetainsTheLastVerdictDuringTheNewProbe() = withHarness {
        succeed()
        clock.set(90_000)
        assertTrue(monitor.read().isConnected)
        probe.awaitRequests(2)
        assertEquals(InternetAccess.AVAILABLE, monitor.states.value.internetAccess)
    }

    @Test(timeout = 5_000)
    fun successfulConfirmationAfterOneFailureDoesNotFlickerOffline() = withHarness {
        val observed = observe()
        succeed()
        advanceTo(3_000, 2)
        probe.complete(false, 1)
        assertTrue(monitor.read().isConnected)
        advanceTo(4_000, 3)
        probe.complete(true, 2)
        assertEquals(listOf(true), observed.toList())
    }

    @Test(timeout = 5_000)
    fun repeatedSuccessesDoNotPublishNewVerdictsOrBooleanNotifications() = withHarness {
        val observed = observe()
        succeed()
        val generation = monitor.states.value.generation
        for (index in 1..3) {
            advanceTo(index * 3_000L, index + 1)
            probe.complete(true, index)
            assertEquals(generation, monitor.states.value.generation)
        }
        assertEquals(listOf(true), observed.toList())
    }

    @Test(timeout = 5_000)
    fun routeMetadataChurnDoesNotRepeatOfflineNotifications() = withHarness {
        val observed = observe()
        probe.awaitRequests(1)
        confirmFailure()
        repeat(3) {
            raw.value = raw.value.copy(generation = raw.value.generation + 1, transports = setOf(it))
            monitor.read()
        }
        assertEquals(listOf(false), observed.toList())
    }

    @Test(timeout = 5_000)
    fun repeatedReadsShareChecksAndOfflineRetryDelayIsCappedAtFiveSeconds() = withHarness {
        probe.awaitRequests(1)
        repeat(100) { monitor.read() }
        assertEquals(1, probe.requests.size)
        confirmFailure()
        repeat(100) { monitor.read() }
        assertEquals(2, probe.requests.size)
        advanceTo(2_000, 3)
        probe.complete(false, 2)
        clock.set(3_999)
        monitor.read()
        assertEquals(3, probe.requests.size)
        advanceTo(4_000, 4)
        probe.complete(false, 3)
        clock.set(8_999)
        monitor.read()
        assertEquals(4, probe.requests.size)
        advanceTo(9_000, 5)
        probe.complete(false, 4)
        advanceTo(14_000, 6)
    }

    @Test(timeout = 5_000)
    fun staleProbeCompletionCannotMarkANewRouteOnline() = withHarness {
        probe.awaitRequests(1)
        raw.value = raw.value.copy(networkId = 2, isVpn = true, generation = 2)
        monitor.read()
        probe.awaitRequests(2)
        probe.complete(true, 0)
        assertFalse(monitor.read().isConnected)
        confirmFailure(1)
        assertEquals(2L, monitor.states.value.networkId)
    }

    @Test(timeout = 5_000)
    fun routeChangeCancelsTheOldCheckWithoutDroppingConfirmedOnline() = withHarness {
        succeed()
        advanceTo(3_000, 2)
        raw.value = raw.value.copy(networkId = 2, isVpn = true, generation = 2)
        assertTrue(monitor.read().isConnected)
        probe.awaitRequests(3)
        probe.complete(false, 1)
        assertTrue(monitor.read().isConnected)
        probe.complete(true, 2)
        assertTrue(probe.cancelled.get() > 0)
        assertTrue(monitor.read().isConnected)
    }

    @Test(timeout = 5_000)
    fun stopRestartDoesNotReuseAnOldVerdictOrRequest() = withHarness {
        probe.awaitRequests(1)
        monitor.stop()
        assertFalse(monitor.states.value.isConnected)
        monitor.start()
        probe.awaitRequests(2)
        probe.complete(true, 0)
        assertFalse(monitor.read().isConnected)
        probe.complete(true, 1)
        assertTrue(monitor.read().isConnected)
    }

    @Test(timeout = 5_000)
    fun unvalidatedInternetRouteCanBecomeOnlineThroughSuccessfulHttps() = withHarness(
        initial = NetworkState(NetworkAvailability.UNVALIDATED, 1, isVpn = true, generation = 1)
    ) {
        assertFalse(raw.value.isConnected)
        succeed()
        assertEquals(NetworkAvailability.UNVALIDATED, monitor.states.value.availability)
    }

    @Test(timeout = 5_000)
    fun recoveryIsAutomaticAndDoesNotRequireConsumerCalls() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val raw = MutableStateFlow(online())
        val calls = AtomicInteger()
        val monitor = InternetConnectivityMonitor(raw, { raw.value }, { calls.incrementAndGet() >= 3 }, scope,
            clockMillis = { System.nanoTime() / 1_000_000 }, confirmationMillis = 20,
            recheckMillis = 1_000, retryMillis = listOf(20))
        try {
            monitor.start()
            await { monitor.states.value.isConnected }
            assertEquals(3, calls.get())
        } finally { monitor.stop(); scope.cancel() }
    }

    @Test(timeout = 5_000)
    fun backgroundRecheckUsesTwoFailuresBeforeNotifyingOffline() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val raw = MutableStateFlow(online())
        val calls = AtomicInteger()
        val monitor = InternetConnectivityMonitor(raw, { raw.value }, { calls.incrementAndGet() == 1 }, scope,
            clockMillis = { System.nanoTime() / 1_000_000 }, recheckMillis = 50,
            confirmationMillis = 20, retryMillis = listOf(1_000))
        try {
            monitor.start()
            await { monitor.states.value.isConnected }
            await { !monitor.states.value.isConnected }
            assertEquals(3, calls.get())
            assertTrue(raw.value.isConnected)
        } finally { monitor.stop(); scope.cancel() }
    }

    @Test(timeout = 5_000)
    fun sharedBooleanNotificationsContainOnlyConfirmedTransitions() = withHarness {
        val observed = observe()
        succeed()
        advanceTo(3_000, 2)
        confirmFailure(1)
        assertEquals(listOf(true, false), observed.toList())
    }

    @Test(timeout = 5_000)
    fun collectorCanReadMonitorFromAnotherThreadWithoutHeldLocks() = withHarness {
        probe.awaitRequests(1)
        val observer = scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            monitor.states.drop(1).take(1).collect {
                val read = FutureTask { monitor.read() }
                Thread(read).apply { isDaemon = true }.start()
                assertTrue(read.get(2, TimeUnit.SECONDS).isConnected)
            }
        }
        probe.complete(true)
        observer.join()
    }

    @Test(timeout = 5_000)
    fun aSingleCheckExceptionDoesNotDropConfirmedOnlineAndTheRetryRecovers() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val raw = MutableStateFlow(online())
        val calls = AtomicInteger()
        val observed = CopyOnWriteArrayList<Boolean>()
        val monitor = InternetConnectivityMonitor(raw, { raw.value }, {
            if (calls.incrementAndGet() == 2) throw IOException("Transport unavailable")
            true
        }, scope, clockMillis = { System.nanoTime() / 1_000_000 }, recheckMillis = 20,
            confirmationMillis = 20)
        val observer = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            monitor.connectionChanges.collect { observed.add(it) }
        }
        try {
            monitor.start()
            await { calls.get() >= 3 }
            assertTrue(monitor.states.value.isConnected)
            assertEquals(listOf(true), observed.toList())
        } finally { observer.cancel(); monitor.stop(); scope.cancel() }
    }

    @Test(timeout = 5_000)
    fun metadataPollingRestoresOnlineEvenWhenAndroidCallbacksAreMissing() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val raw = MutableStateFlow(NetworkState(NetworkAvailability.UNAVAILABLE, generation = 1))
        val transportRestored = AtomicBoolean(false)
        val monitor = InternetConnectivityMonitor(raw, {
            if (transportRestored.get()) raw.value = online().copy(generation = 2)
            raw.value
        }, { true }, scope, clockMillis = { System.nanoTime() / 1_000_000 },
            confirmationMillis = 20, retryMillis = listOf(20))
        try {
            monitor.start()
            await { monitor.states.value.internetAccess == InternetAccess.UNAVAILABLE }
            transportRestored.set(true)
            await { monitor.states.value.isConnected }
        } finally { monitor.stop(); scope.cancel() }
    }

    @Test(timeout = 5_000)
    fun unusableMetadataChurnDoesNotRestartTheContinuousOfflineGracePeriod() = withHarness {
        val observed = observe()
        succeed()
        raw.value = NetworkState(NetworkAvailability.UNAVAILABLE, generation = 2)
        assertTrue(monitor.read().isConnected)
        for (time in listOf(400L, 800L)) {
            clock.set(time)
            raw.value = raw.value.copy(generation = raw.value.generation + 1)
            assertTrue(monitor.read().isConnected)
        }
        clock.set(1_200)
        raw.value = raw.value.copy(generation = raw.value.generation + 1)
        assertFalse(monitor.read().isConnected)
        assertEquals(listOf(true, false), observed.toList())
    }

    @Test(timeout = 5_000)
    fun aConfirmedFailureSurvivesMetadataChangesUntilRecoveryIsProven() = withHarness {
        succeed()
        advanceTo(3_000, 2)
        probe.complete(false, 1)
        clock.set(3_500)
        raw.value = raw.value.copy(generation = 2)
        assertTrue(monitor.read().isConnected)
        probe.awaitRequests(3)
        probe.complete(false, 2)
        assertFalse(monitor.read().isConnected)
    }

    @Test(timeout = 5_000)
    fun usableMetadataChurnCannotContinuouslyCancelAStalledCheck() = withHarness {
        succeed()
        advanceTo(3_000, 2)
        for (time in listOf(3_300L, 3_600L, 3_900L)) {
            clock.set(time)
            raw.value = raw.value.copy(generation = raw.value.generation + 1)
            assertTrue(monitor.read().isConnected)
            assertEquals(2, probe.requests.size)
        }
        probe.complete(false, 1)
        advanceTo(4_900, 3)
        probe.complete(false, 2)
        assertFalse(monitor.read().isConnected)
    }

    @Test(timeout = 5_000)
    fun backgroundChecksAreSparseAndForegroundReturnChecksImmediately() = withHarness {
        val observed = observe()
        succeed()
        monitor.setForeground(false)
        clock.set(3_000)
        assertTrue(monitor.read().isConnected)
        assertEquals(1, probe.requests.size)
        advanceTo(60_000, 2)
        probe.complete(true, 1)
        clock.set(60_010)
        monitor.setForeground(true)
        probe.awaitRequests(3)
        assertTrue(monitor.read().isConnected)
        probe.complete(true, 2)
        assertEquals(listOf(true), observed.toList())
    }

    @Test(timeout = 5_000)
    fun ordinaryBackgroundMetadataCannotBypassTheSparseCheckSchedule() = withHarness {
        succeed()
        monitor.setForeground(false)
        for (time in listOf(1_000L, 10_000L, 59_999L)) {
            clock.set(time)
            raw.value = raw.value.copy(generation = raw.value.generation + 1)
            assertTrue(monitor.read().isConnected)
            assertEquals(1, probe.requests.size)
        }
        advanceTo(60_000, 2)
    }

    @Test(timeout = 5_000)
    fun backgroundValidationAndTransportChangesStillCheckImmediately() = withHarness {
        succeed()
        monitor.setForeground(false)
        raw.value = raw.value.copy(availability = NetworkAvailability.UNVALIDATED, generation = 2)
        monitor.read()
        probe.awaitRequests(2)
        probe.complete(true, 1)
        raw.value = raw.value.copy(transports = setOf(3), generation = 3)
        monitor.read()
        probe.awaitRequests(3)
        assertTrue(monitor.read().isConnected)
    }

    @Test(timeout = 5_000)
    fun enteringBackgroundDoesNotPostponeTheSecondFailedCheck() = withHarness {
        succeed()
        advanceTo(3_000, 2)
        probe.complete(false, 1)
        monitor.setForeground(false)
        advanceTo(4_000, 3)
        probe.complete(false, 2)
        assertFalse(monitor.read().isConnected)
        clock.set(9_000)
        monitor.read()
        assertEquals(3, probe.requests.size)
        advanceTo(64_000, 4)
    }

    @Test(timeout = 5_000)
    fun initialNoRouteConfirmationIsNotPostponedByBackgroundThrottling() = withHarness(
        initial = NetworkState(NetworkAvailability.UNAVAILABLE, generation = 1)
    ) {
        monitor.setForeground(false)
        clock.set(1_000)
        assertEquals(InternetAccess.UNAVAILABLE, monitor.read().internetAccess)
        assertTrue(probe.requests.isEmpty())
    }

    private class FakeCheck {
        class Request(val state: NetworkState, val continuation: CancellableContinuation<Boolean>) {
            val finished = AtomicBoolean(false)
        }
        val requests = CopyOnWriteArrayList<Request>()
        val cancelled = AtomicInteger()
        suspend fun check(state: NetworkState): Boolean = suspendCancellableCoroutine { continuation ->
            val request = Request(state, continuation)
            continuation.invokeOnCancellation { request.finished.set(true); cancelled.incrementAndGet() }
            requests.add(request)
        }
        suspend fun awaitRequests(count: Int) = await { requests.size >= count }
        suspend fun awaitRoute(id: Long): Int {
            await { requests.any { it.state.networkId == id } }
            return requests.indexOfLast { it.state.networkId == id }
        }
        fun complete(available: Boolean, index: Int = 0) {
            val request = requests[index]
            if (request.finished.compareAndSet(false, true)) request.continuation.resume(available)
        }
    }

    private class Harness(initial: NetworkState, timeout: Long) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val raw = MutableStateFlow(initial)
        val clock = AtomicLong(0)
        val probe = FakeCheck()
        val monitor = InternetConnectivityMonitor(raw, { raw.value }, probe::check, scope, clock::get, timeout,
            recheckMillis = 3_000)
        fun observe(): CopyOnWriteArrayList<Boolean> = CopyOnWriteArrayList<Boolean>().also { observed ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                monitor.connectionChanges.collect { observed.add(it) }
            }
        }
        suspend fun succeed(index: Int = 0) {
            probe.awaitRequests(index + 1)
            probe.complete(true, index)
            await { monitor.states.value.isConnected }
        }
        suspend fun advanceTo(millis: Long, requests: Int) {
            clock.set(millis)
            await { monitor.read(); probe.requests.size >= requests }
        }
        suspend fun confirmFailure(index: Int = 0) {
            probe.complete(false, index)
            advanceTo(clock.get() + 1_000, index + 2)
            probe.complete(false, index + 1)
            await { monitor.states.value.internetAccess == InternetAccess.UNAVAILABLE }
        }
    }

    private fun withHarness(initial: NetworkState = online(), timeout: Long = 1_500,
        block: suspend Harness.() -> Unit) = runBlocking {
        val harness = Harness(initial, timeout)
        try { harness.monitor.start(); harness.block() }
        finally { harness.monitor.stop(); harness.scope.cancel() }
    }

    companion object {
        private fun online() = NetworkState(NetworkAvailability.VALIDATED, 1, generation = 1)
        private suspend fun await(condition: () -> Boolean) {
            withTimeout(2_000) { while (!condition()) delay(1) }
        }
    }
}
