package dev.skomlach.common.network

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NetworkAccessPolicyCacheTest {
    @Test
    fun failedLookupIsRetriedAndSuccessfulDefaultsAreCached() {
        var attempts = 0
        val cache = NetworkAccessPolicyCache {
            if (++attempts == 1) throw IOException("metadata unavailable")
            NetworkAccessPolicy.read { _, default -> default }
        }

        assertEquals(NetworkAccessPolicy.DISABLED, cache.read())
        assertNull(cache.states.value)
        val policy = cache.read()
        assertTrue(policy.networkEnabled)
        assertFalse(policy.deviceDatasetRefreshEnabled)
        assertSame(policy, cache.states.value)
        assertSame(policy, cache.read())
        assertEquals(2, attempts)
    }

    @Test
    fun explicitOfflineAfterFailureIsCachedAndOverridesDatasetOptIn() {
        var attempts = 0
        val cache = NetworkAccessPolicyCache {
            if (++attempts == 1) throw IOException("metadata unavailable")
            NetworkAccessPolicy.read { name, _ -> name != NetworkAccessPolicy.NETWORK_ENABLED }
        }

        assertEquals(NetworkAccessPolicy.DISABLED, cache.read())
        assertNull(cache.states.value)
        assertEquals(NetworkAccessPolicy.DISABLED, cache.read())
        assertEquals(NetworkAccessPolicy.DISABLED, cache.states.value)
        assertEquals(NetworkAccessPolicy.DISABLED, cache.read())
        assertEquals(2, attempts)
    }

    @Test
    fun malformedLoadedSettingsStayDisabledWithoutRetrying() {
        var attempts = 0
        val cache = NetworkAccessPolicyCache {
            attempts++
            NetworkAccessPolicy.read { _, _ -> throw IllegalArgumentException("Expected Boolean metadata") }
        }

        assertEquals(NetworkAccessPolicy.DISABLED, cache.read())
        assertEquals(NetworkAccessPolicy.DISABLED, cache.states.value)
        assertEquals(NetworkAccessPolicy.DISABLED, cache.read())
        assertEquals(1, attempts)
    }

    @Test
    fun resolvedSettingsAreImmutableEvenIfTheBackingSourceChanges() {
        var source = NetworkAccessPolicy(true, true)
        var attempts = 0
        val cache = NetworkAccessPolicyCache { attempts++; source }
        val original = cache.read()

        source = NetworkAccessPolicy.DISABLED
        assertSame(original, cache.read())
        assertSame(original, cache.states.value)
        assertEquals(1, attempts)
    }

    @Test
    fun concurrentRecoveryLoadsOnePolicy() {
        val attempts = AtomicInteger()
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val recovered = NetworkAccessPolicy(true, true)
        val cache = NetworkAccessPolicyCache {
            if (attempts.incrementAndGet() == 1) throw IOException("metadata unavailable")
            recovered
        }
        assertEquals(NetworkAccessPolicy.DISABLED, cache.read())

        val pool = Executors.newFixedThreadPool(8)
        try {
            val calls = (1..8).map {
                pool.submit(Callable {
                    ready.countDown()
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    cache.read()
                })
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            calls.forEach { assertSame(recovered, it.get(5, TimeUnit.SECONDS)) }
            assertSame(recovered, cache.states.value)
            assertEquals(2, attempts.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun onlySuccessfulResolutionNotifiesObserversAndTheyCanReadTheCache() = runBlocking {
        var attempts = 0
        val cache = NetworkAccessPolicyCache {
            if (++attempts == 1) throw IOException("metadata unavailable")
            NetworkAccessPolicy(true, false)
        }
        val observed = mutableListOf<NetworkAccessPolicy>()
        val observer = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            cache.states.filterNotNull().collect { policy ->
                assertSame(policy, cache.read())
                observed += policy
            }
        }
        try {
            cache.read()
            assertTrue(observed.isEmpty())
            val recovered = cache.read()
            cache.read()
            assertEquals(listOf(recovered), observed)
            assertEquals(2, attempts)
        } finally {
            observer.cancel()
        }
    }
}
