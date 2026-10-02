/*
 *  Copyright (c) 2026 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package dev.skomlach.common.network

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.io.IOException
import javax.net.ssl.SSLException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

enum class ReachabilityStatus {
    REACHABLE, UNREACHABLE, TLS_ERROR, TIMED_OUT, NETWORK_CHANGED, NO_NETWORK, BLOCKED, UNKNOWN, SUSPENDED
}

data class ReachabilityResult(
    val status: ReachabilityStatus,
    val network: NetworkState,
    /** Any HTTP response proves reachability; its status still needs application handling. */
    val httpStatusCode: Int? = null
)

sealed class ProbeResponse {
    data class Http(val statusCode: Int) : ProbeResponse() {
        init { require(statusCode in 100..599) }
    }
    object Unreachable : ProbeResponse()
    object TlsError : ProbeResponse()
}

fun interface ProbeCancellation {
    /** Must be thread-safe/non-blocking and abort the underlying request, not just its callback. */
    fun cancel()
}

fun interface EndpointProbe {
    /**
     * Start an asynchronous request using the application's HTTP client and TLS policy.
     * This method must return promptly without DNS/socket I/O. Follow the effective process
     * route (including VPN); do not select an underlying Wi-Fi/mobile network to bypass it.
     * Close the response resources before completing. Completion may race with cancellation.
     * Disable automatic redirects so an endpoint's own HTTP response is reported. For the
     * explicit-Network overload, use that caller-selected route for both sockets and DNS.
     */
    fun start(endpoint: String, network: NetworkState, complete: (ProbeResponse) -> Unit): ProbeCancellation
}

internal class NetworkReachability(
    private val states: StateFlow<NetworkState>,
    private val refresh: () -> NetworkState,
    private val scope: CoroutineScope,
    private val clockMillis: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private class Key(val endpoint: String, val probe: EndpointProbe, val generation: Long, val timeout: Long) {
        override fun equals(other: Any?): Boolean = other is Key && endpoint == other.endpoint &&
            probe === other.probe && generation == other.generation && timeout == other.timeout
        override fun hashCode(): Int = 31 * (31 * (31 * endpoint.hashCode() +
            System.identityHashCode(probe)) + generation.hashCode()) + timeout.hashCode()
    }
    private class Flight(val result: Deferred<Cached>, var waiters: Int = 0)
    private data class Cached(val result: ReachabilityResult, val completedAt: Long, val sequence: Long)
    private val mutex = Mutex()
    private val flights = mutableMapOf<Key, Flight>()
    private val cache = LinkedHashMap<Key, Cached>()
    private var flightSequence = 0L

    suspend fun check(
        endpoint: String, probe: EndpointProbe, timeoutMillis: Long, cacheMillis: Long
    ): ReachabilityResult {
        val uri = URI(endpoint)
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port in 1..65535)) { "A HTTPS endpoint without user info or fragment and with a valid port is required" }
        require(timeoutMillis in 1..60_000L) { "Probe timeout must be between 1 and 60000 ms" }
        require(cacheMillis in 0..60_000L) { "Probe cache must be between 0 and 60000 ms" }
        val network = refresh()
        val unavailable = when (network.availability) {
            NetworkAvailability.UNKNOWN -> ReachabilityStatus.UNKNOWN
            NetworkAvailability.UNAVAILABLE -> ReachabilityStatus.NO_NETWORK
            NetworkAvailability.BLOCKED -> ReachabilityStatus.BLOCKED
            NetworkAvailability.SUSPENDED -> ReachabilityStatus.SUSPENDED
            else -> null
        }
        if (unavailable != null) return ReachabilityResult(unavailable, network)
        val key = Key(endpoint, probe, network.generation, timeoutMillis)
        val flight = mutex.withLock {
            if (states.value.generation != network.generation) {
                return ReachabilityResult(ReachabilityStatus.NETWORK_CHANGED, network)
            }
            cache.entries.removeAll { it.key.generation != network.generation }
            cache[key]?.let {
                if (cacheMillis > 0 && clockMillis() - it.completedAt < cacheMillis) return it.result
            }
            // A completed flight is not an implicit cache: its previous waiter may be paused.
            (flights[key]?.takeUnless { it.result.isCompleted } ?: run {
                val sequence = ++flightSequence
                Flight(scope.async(start = CoroutineStart.LAZY) {
                    Cached(execute(endpoint, probe, network, timeoutMillis), clockMillis(), sequence)
                }).also { flights[key] = it }
            }).also { it.waiters++ }
        }
        try {
            val completed = withTimeoutOrNull(timeoutMillis) { flight.result.await() }
            val result = completed?.result ?: ReachabilityResult(ReachabilityStatus.TIMED_OUT, network)
            if (states.value.generation != network.generation) {
                return ReachabilityResult(ReachabilityStatus.NETWORK_CHANGED, network)
            }
            if (result.status == ReachabilityStatus.REACHABLE && cacheMillis > 0) {
                mutex.withLock {
                    if (states.value.generation == network.generation &&
                        (cache[key]?.sequence ?: Long.MIN_VALUE) < requireNotNull(completed).sequence) {
                        cache[key] = requireNotNull(completed)
                        if (cache.size > MAX_CACHE_ENTRIES) cache.remove(cache.keys.first())
                    }
                }
            }
            return result
        } finally {
            withContext(NonCancellable) {
                val lastWaiter = mutex.withLock {
                    flight.waiters--
                    if (flight.waiters == 0 && flights[key] === flight) flights.remove(key)
                    flight.waiters == 0 && !flight.result.isCompleted
                }
                // Do not join a worker queued behind unrelated tasks. Cancellation propagates
                // to the request; the handle handshake also cancels handles installed later.
                if (lastWaiter) flight.result.cancel()
            }
        }
    }

    private suspend fun execute(
        endpoint: String, probe: EndpointProbe, network: NetworkState, timeoutMillis: Long
    ): ReachabilityResult = coroutineScope {
        val request = async(start = CoroutineStart.LAZY) {
            withTimeoutOrNull(timeoutMillis) { awaitProbe(endpoint, probe, network) }
        }
        val routeWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
            states.first { it.generation != network.generation }
            request.cancel(RouteChanged())
        }
        try {
            val response = request.await()
            when (response) {
                null -> ReachabilityResult(ReachabilityStatus.TIMED_OUT, network)
                is ProbeResponse.Http -> ReachabilityResult(ReachabilityStatus.REACHABLE, network, response.statusCode)
                ProbeResponse.Unreachable -> ReachabilityResult(ReachabilityStatus.UNREACHABLE, network)
                ProbeResponse.TlsError -> ReachabilityResult(ReachabilityStatus.TLS_ERROR, network)
            }
        } catch (_: RouteChanged) {
            ReachabilityResult(ReachabilityStatus.NETWORK_CHANGED, network)
        } finally {
            routeWatcher.cancel()
        }
    }

    private suspend fun awaitProbe(
        endpoint: String, probe: EndpointProbe, network: NetworkState
    ): ProbeResponse = suspendCancellableCoroutine { continuation ->
        val finished = AtomicBoolean(false)
        val cancellation = AtomicReference<ProbeCancellation?>(null)
        continuation.invokeOnCancellation {
            finished.set(true)
            cancellation.getAndSet(CANCELLED)?.let { if (it !== CANCELLED) cancelSafely(it) }
        }
        try {
            val handle = probe.start(endpoint, network) { response ->
                if (finished.compareAndSet(false, true)) continuation.resume(response)
            }
            if (!cancellation.compareAndSet(null, handle)) cancelSafely(handle)
        } catch (e: Exception) {
            if (finished.compareAndSet(false, true)) {
                when (e) {
                    is SSLException -> continuation.resume(ProbeResponse.TlsError)
                    is IOException -> continuation.resume(ProbeResponse.Unreachable)
                    else -> continuation.resumeWith(Result.failure(e))
                }
            }
        }
    }

    private fun cancelSafely(handle: ProbeCancellation) {
        try { handle.cancel() } catch (_: Exception) { /* Do not strand the cancelled waiter. */ }
    }

    private class RouteChanged : CancellationException("Network route changed")

    companion object {
        private const val MAX_CACHE_ENTRIES = 16
        private val CANCELLED = ProbeCancellation { }
    }
}
