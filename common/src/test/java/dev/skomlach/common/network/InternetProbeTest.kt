package dev.skomlach.common.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class InternetProbeTest {
    @Test(timeout = 5_000)
    fun unavailableOneProviderDoesNotMakeTheWholeInternetOffline() = withHarness {
        val probe = EndpointProbe { endpoint, _, complete ->
            complete(if (endpoint.contains("first")) ProbeResponse.Unreachable else ProbeResponse.Http(204))
            ProbeCancellation { }
        }
        assertTrue(group(probe).check())
    }

    @Test(timeout = 5_000)
    fun allFailedProvidersAreOffline() = withHarness {
        val probe = EndpointProbe { _, _, complete -> complete(ProbeResponse.Unreachable); ProbeCancellation { } }
        assertFalse(group(probe).check())
    }

    @Test(timeout = 5_000)
    fun respondingServersProveInternetReachabilityEvenWithAnHttpError() = withHarness {
        val probe = EndpointProbe { _, _, complete -> complete(ProbeResponse.Http(503)); ProbeCancellation { } }
        assertTrue(group(probe).check())
    }

    @Test(timeout = 5_000)
    fun successCancelsTheOtherStalledProvider() = withHarness {
        val waiting = WaitingProbe()
        val pending = async { group(waiting).check() }
        await { waiting.callbacks.size == 2 }
        waiting.callbacks.values.first()(ProbeResponse.Http(204))
        assertTrue(pending.await())
        assertEquals(1, waiting.cancelled.get())
    }

    @Test(timeout = 5_000)
    fun outerDeadlineCancelsBothStalledRequests() = withHarness {
        val waiting = WaitingProbe()
        val pending = async { group(waiting).check() }
        await { waiting.callbacks.size == 2 }
        pending.cancelAndJoin()
        assertEquals(2, waiting.cancelled.get())
    }

    @Test(timeout = 5_000)
    fun automaticOfflineConfirmationCancelsBothProvidersOnBothAttempts() = withHarness {
        val waiting = WaitingProbe()
        val monitor = InternetConnectivityMonitor(raw, { raw.value }, { group(waiting).check() }, worker,
            clockMillis = { System.nanoTime() / 1_000_000 }, timeoutMillis = 50,
            confirmationMillis = 20, retryMillis = listOf(1_000))
        try {
            monitor.start()
            await { monitor.states.value.internetAccess == InternetAccess.UNAVAILABLE }
            assertEquals(4, waiting.started.get())
            assertEquals(4, waiting.cancelled.get())
            assertTrue(raw.value.isConnected)
        } finally { monitor.stop() }
    }

    @Test(timeout = 5_000)
    fun routeChangeCannotValidateTheOldInternetPath() = withHarness {
        val waiting = WaitingProbe()
        val pending = async { group(waiting).check() }
        await { waiting.callbacks.size == 2 }
        raw.value = raw.value.copy(networkId = 2, generation = 2)
        assertFalse(pending.await())
        assertEquals(2, waiting.cancelled.get())
    }

    @Test fun configurableHostsAreImmutableAndRejectCredentialsOrCleartext() {
        val input = mutableListOf("https://example.com/check", "https://example.com/check")
        val configured = InternetProbeConfiguration.validate(input)
        input.clear()
        assertEquals(listOf("https://example.com/check"), configured)
        for (endpoint in listOf("http://example.com/", "https://user:secret@example.com/",
            "https://example.com/?token=secret", "https://example.com/#secret", "https://example.com:0/", "bad URL")) {
            assertThrows(IllegalArgumentException::class.java) { InternetProbeConfiguration.validate(listOf(endpoint)) }
        }
        assertThrows(IllegalArgumentException::class.java) { InternetProbeConfiguration.validate(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { InternetProbeConfiguration.validate(List(5) { "https://example.com/" }) }
    }

    @Test(timeout = 5_000)
    fun automaticRouteSnapshotDoesNotRestartOnMetadataGenerationChanges() = withHarness {
        val waiting = WaitingProbe()
        val pending = async { group(waiting).check(raw.value) }
        await { waiting.callbacks.size == 2 }
        raw.value = raw.value.copy(generation = 2)
        raw.value = raw.value.copy(generation = 3)
        waiting.callbacks.values.first()(ProbeResponse.Http(204))
        assertTrue(pending.await())
        assertEquals(2, waiting.started.get())
        assertEquals(1, waiting.cancelled.get())
    }

    @Test(timeout = 5_000)
    fun routeLossStillCancelsBothAutomaticSnapshotRequests() = withHarness {
        val waiting = WaitingProbe()
        val monitor = InternetConnectivityMonitor(raw, { raw.value }, { state -> group(waiting).check(state) }, worker,
            clockMillis = { System.nanoTime() / 1_000_000 })
        try {
            monitor.start()
            await { waiting.callbacks.size == 2 }
            raw.value = NetworkState(NetworkAvailability.UNAVAILABLE, generation = 2)
            await { waiting.cancelled.get() == 2 }
            assertFalse(monitor.read().isConnected)
        } finally { monitor.stop() }
    }

    @Test(timeout = 5_000)
    fun aFastKnownHealthyEndpointAvoidsStartingTheBackup() = withHarness {
        val waiting = WaitingProbe()
        val group = group(waiting)
        confirmPreferredEndpoint(group, waiting, SECOND)
        val next = async { group.check(raw.value) }
        await { waiting.started.get() == 3 }
        assertEquals(setOf(SECOND), waiting.callbacks.keys.toSet())
        waiting.callbacks.getValue(SECOND)(ProbeResponse.Http(503))
        assertTrue(next.await())
        assertEquals(3, waiting.started.get())
    }

    @Test(timeout = 5_000)
    fun aKnownProviderFailureStartsTheBackupWithoutWaitingForTheHedge() = withHarness {
        val waiting = WaitingProbe()
        val group = group(waiting)
        confirmPreferredEndpoint(group, waiting)
        val next = async { group.check(raw.value) }
        await { waiting.started.get() == 3 }
        waiting.callbacks.getValue(FIRST)(ProbeResponse.Unreachable)
        // This dispatcher is a single test event loop: the backup can start before any delay.
        yield()
        withTimeout(InternetProbe.HEDGE_DELAY_MILLIS / 2) { await { waiting.started.get() == 4 } }
        waiting.callbacks.getValue(SECOND)(ProbeResponse.Http(204))
        assertTrue(next.await())
        waiting.callbacks.clear()
        val preferred = async { group.check(raw.value) }
        await { waiting.started.get() == 5 }
        assertEquals(setOf(SECOND), waiting.callbacks.keys.toSet())
        waiting.callbacks.getValue(SECOND)(ProbeResponse.Http(204))
        assertTrue(preferred.await())
    }

    @Test(timeout = 5_000)
    fun aSilentPreferredProviderStillAllowsTheAlternateItsFullDeadline() = withHarness {
        val waiting = WaitingProbe()
        val group = group(waiting)
        confirmPreferredEndpoint(group, waiting)
        val next = async { withTimeoutOrNull(InternetProbe.ROUND_TIMEOUT_MILLIS) { group.check(raw.value) } }
        await { waiting.started.get() == 4 }
        delay(InternetProbe.TIMEOUT_MILLIS - InternetProbe.HEDGE_DELAY_MILLIS + 50)
        waiting.callbacks.getValue(SECOND)(ProbeResponse.Http(204))
        assertEquals(true, next.await())
        assertTrue(waiting.cancelled.get() >= 2)
    }

    @Test(timeout = 5_000)
    fun routeMetadataChangesResetThePreferenceAndCheckAllProviders() = withHarness {
        val waiting = WaitingProbe()
        val group = group(waiting)
        confirmPreferredEndpoint(group, waiting)
        raw.value = raw.value.copy(generation = 2, isVpn = true, transports = setOf(4, 3))
        val next = async { group.check(raw.value) }
        await { waiting.started.get() == 4 }
        assertEquals(setOf(FIRST, SECOND), waiting.callbacks.keys.toSet())
        waiting.callbacks.getValue(SECOND)(ProbeResponse.Http(204))
        assertTrue(next.await())
    }

    @Test(timeout = 5_000)
    fun lateOldConfigurationSuccessCannotReplaceTheNewPreference() = withHarness {
        val waiting = WaitingProbe()
        val group = group(waiting)
        val old = async { group.check(raw.value) }
        await { waiting.started.get() == 2 }
        val oldReply = waiting.callbacks.getValue(FIRST)
        group.configure(listOf("https://new.example/check", "https://backup.example/check"))
        val changed = async { group.check(raw.value) }
        await { waiting.started.get() == 4 }
        waiting.callbacks.getValue("https://new.example/check")(ProbeResponse.Http(204))
        assertTrue(changed.await())
        oldReply(ProbeResponse.Http(204))
        assertTrue(old.await())
        waiting.callbacks.clear()
        val next = async { group.check(raw.value) }
        await { waiting.started.get() == 5 }
        assertEquals(setOf("https://new.example/check"), waiting.callbacks.keys.toSet())
        waiting.callbacks.getValue("https://new.example/check")(ProbeResponse.Http(204))
        assertTrue(next.await())
    }

    @Test(timeout = 5_000)
    fun cancellationClearsThePreferenceForTheNextConfirmation() = withHarness {
        val waiting = WaitingProbe()
        val group = group(waiting)
        confirmPreferredEndpoint(group, waiting)
        val cancelled = async { group.check(raw.value) }
        await { waiting.started.get() == 3 }
        cancelled.cancelAndJoin()
        waiting.callbacks.clear()
        val next = async { group.check(raw.value) }
        await { waiting.started.get() == 5 }
        assertEquals(setOf(FIRST, SECOND), waiting.callbacks.keys.toSet())
        waiting.callbacks.getValue(SECOND)(ProbeResponse.Http(204))
        assertTrue(next.await())
    }

    @Test(timeout = 5_000)
    fun fourConfiguredProvidersCanFindAResponseFromTheLastOne() = withHarness {
        val waiting = WaitingProbe()
        val group = InternetProbe(checker, waiting, (1..4).map { "https://host$it.example/check" })
        val pending = async { group.check(raw.value) }
        await { waiting.started.get() == 4 }
        waiting.callbacks.getValue("https://host4.example/check")(ProbeResponse.Http(204))
        assertTrue(pending.await())
        assertEquals(3, waiting.cancelled.get())
    }

    private companion object {
        const val FIRST = "https://first.example/check"
        const val SECOND = "https://second.example/check"
    }

    private class WaitingProbe : EndpointProbe {
        val callbacks = ConcurrentHashMap<String, (ProbeResponse) -> Unit>()
        val cancelled = AtomicInteger()
        val started = AtomicInteger()
        override fun start(endpoint: String, network: NetworkState, complete: (ProbeResponse) -> Unit): ProbeCancellation {
            started.incrementAndGet()
            callbacks[endpoint] = complete
            return ProbeCancellation { cancelled.incrementAndGet() }
        }
    }

    private class Harness(scope: CoroutineScope) : CoroutineScope by scope {
        val worker = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val raw = MutableStateFlow(NetworkState(NetworkAvailability.VALIDATED, 1, generation = 1))
        val checker = NetworkReachability(raw, { raw.value }, worker) { 1_000L }
        fun group(probe: EndpointProbe) = InternetProbe(checker, probe,
            listOf(FIRST, SECOND))
    }

    private suspend fun Harness.confirmPreferredEndpoint(
        group: InternetProbe, waiting: WaitingProbe, endpoint: String = FIRST
    ) {
        val started = waiting.started.get()
        val initial = async { group.check(raw.value) }
        await { waiting.started.get() == started + 2 }
        waiting.callbacks.getValue(endpoint)(ProbeResponse.Http(204))
        assertTrue(initial.await())
        waiting.callbacks.clear()
    }

    private fun withHarness(block: suspend Harness.() -> Unit) = runBlocking {
        val harness = Harness(this)
        try { harness.block() } finally { harness.worker.cancel() }
    }

    private suspend fun await(condition: () -> Boolean) {
        withTimeout(2_000) { while (!condition()) delay(1) }
    }
}
