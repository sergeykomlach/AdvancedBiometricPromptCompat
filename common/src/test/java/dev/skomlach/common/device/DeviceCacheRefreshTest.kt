package dev.skomlach.common.device

import dev.skomlach.common.network.InternetAccess
import dev.skomlach.common.network.NetworkState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DeviceCacheRefreshTest {
    @Test(timeout = 5_000)
    fun healthyColdStartWaitsForConfirmedInternetBeforeFetching() = runBlocking {
        val states = MutableStateFlow(NetworkState(internetAccess = InternetAccess.CHECKING))
        val waiting = async { DeviceCacheRefresh.awaitInternet(states, 1_000) }
        delay(20)
        assertFalse(waiting.isCompleted)
        states.value = NetworkState(internetAccess = InternetAccess.AVAILABLE)
        assertTrue(waiting.await())
    }

    @Test(timeout = 5_000)
    fun confirmedOfflineAndAnUnfinishedCheckDoNotPermitFetching() = runBlocking {
        val states = MutableStateFlow(NetworkState(internetAccess = InternetAccess.UNAVAILABLE))
        assertFalse(DeviceCacheRefresh.awaitInternet(states, 100))
        states.value = NetworkState(internetAccess = InternetAccess.CHECKING)
        assertFalse(DeviceCacheRefresh.awaitInternet(states, 30))
    }

    @Test(timeout = 5_000)
    fun timestampWaitsForAllDatasetsAndDuplicateCallsShareTheBatch() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val refresh = DeviceCacheRefresh()
            val started = AtomicInteger()
            val stamps = AtomicInteger()
            val ready = CompletableDeferred<Boolean>()
            val operations = (1..3).map { suspend { started.incrementAndGet(); ready.await() } }
            val first = refresh.start(scope, operations) { stamps.incrementAndGet() }
            withTimeout(1_000) { while (started.get() != 3) delay(1) }
            assertNull(refresh.start(scope, operations) { stamps.incrementAndGet() })
            assertEquals(0, stamps.get())
            assertEquals(3, started.get())
            ready.complete(true)
            first?.join()
            assertEquals(1, stamps.get())
        } finally { scope.cancel() }
    }

    @Test(timeout = 5_000)
    fun failedDatasetDoesNotAdvanceTimestampAndTheNextCallCanRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val refresh = DeviceCacheRefresh()
            val stamps = AtomicInteger()
            val firstDone = CompletableDeferred<Unit>()
            val first = refresh.start(scope, listOf(suspend { true }, suspend { firstDone.complete(Unit); false })) {
                stamps.incrementAndGet()
            }
            firstDone.await()
            first?.join()
            assertEquals(0, stamps.get())
            val retry = refresh.start(scope, listOf(suspend { true }, suspend { true })) { stamps.incrementAndGet() }
            assertNotNull(retry)
            retry?.join()
            assertEquals(1, stamps.get())
        } finally { scope.cancel() }
    }

    @Test(timeout = 5_000)
    fun cancelledScopeDoesNotPermanentlyBlockAFutureRefresh() = runBlocking {
        val cancelled = CoroutineScope(SupervisorJob() + Dispatchers.Default).apply { cancel() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val refresh = DeviceCacheRefresh()
            val done = CompletableDeferred<Unit>()
            refresh.start(cancelled, listOf(suspend { true })) { fail("Cancelled check must not succeed") }?.join()
            val retry = refresh.start(scope, listOf(suspend { true })) { done.complete(Unit) }
            assertNotNull(retry)
            retry?.join()
            assertTrue(done.isCompleted)
        } finally { scope.cancel() }
    }
}
