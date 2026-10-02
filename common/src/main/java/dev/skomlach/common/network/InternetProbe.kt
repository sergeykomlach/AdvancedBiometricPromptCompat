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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.net.URISyntaxException
import java.util.Collections

/** A responding TLS-authenticated public server proves the route can reach the Internet. */
internal class InternetProbe(
    private val checker: NetworkReachability,
    private val probe: EndpointProbe,
    endpoints: List<String> = DEFAULT_ENDPOINTS
) {
    private data class Preferred(val network: NetworkState, val endpoint: String)
    private data class Plan(val targets: List<String>, val preferred: String?, val ticket: Long)
    private data class Reply(val endpoint: String, val reachable: Boolean)

    init {
        require(endpoints.size in 1..MAX_ENDPOINTS)
    }

    private var endpoints = endpoints.toList()
    private var preferred: Preferred? = null
    private var ticket = 0L

    @Synchronized
    fun configure(endpoints: List<String>): Boolean {
        require(endpoints.size in 1..MAX_ENDPOINTS)
        if (this.endpoints == endpoints) return false
        this.endpoints = endpoints.toList()
        preferred = null
        ticket++
        return true
    }

    @Synchronized
    private fun plan(network: NetworkState?) = Plan(
        targets = endpoints,
        preferred = preferred?.takeIf { it.network == network }?.endpoint,
        ticket = ++ticket
    )

    @Synchronized
    private fun remember(plan: Plan, network: NetworkState?, winner: String?) {
        // A cancelled old route/configuration must not replace a newer check's preference.
        if (ticket != plan.ticket) return
        preferred = if (network != null && winner != null) Preferred(network, winner) else null
    }

    suspend fun check(network: NetworkState? = null): Boolean = coroutineScope {
        // The automatic monitor owns route cancellation. Snapshotting here avoids cancelling
        // a native request on each metadata generation of the same usable Android Network.
        // The public opt-in endpoint API keeps its stricter generation-based invalidation.
        val actualChecker = if (network == null) checker else NetworkReachability(
            MutableStateFlow(network), { network }, this, { System.nanoTime() / 1_000_000 }
        )
        val plan = plan(network)
        val targets = plan.preferred?.let { first ->
            listOf(first) + plan.targets.filter { it != first }
        } ?: plan.targets
        val firstFailed = CompletableDeferred<Unit>()
        val replies = Channel<Reply>(targets.size)
        val checks = targets.map { endpoint ->
            launch(start = CoroutineStart.UNDISPATCHED) {
                if (plan.preferred != null && endpoint != plan.preferred) {
                    // Fast healthy checks need one request. A failure starts backups immediately;
                    // a silent stall starts them after a short hedge, retaining their full deadline.
                    withTimeoutOrNull(HEDGE_DELAY_MILLIS) { firstFailed.await() }
                }
                ensureActive()
                val result = actualChecker.check(endpoint, probe, TIMEOUT_MILLIS, 0)
                val reachable = result.status == ReachabilityStatus.REACHABLE
                if (endpoint == plan.preferred && !reachable) firstFailed.complete(Unit)
                replies.send(Reply(endpoint, reachable))
            }
        }
        var winner: String? = null
        try {
            repeat(targets.size) {
                val reply = replies.receive()
                if (reply.reachable) {
                    winner = reply.endpoint
                    return@coroutineScope true
                }
            }
            false
        } finally {
            remember(plan, network, if (isActive) winner else null)
            checks.forEach { it.cancel() }
            replies.cancel()
        }
    }

    companion object {
        const val TIMEOUT_MILLIS = 2_000L
        const val HEDGE_DELAY_MILLIS = 250L
        const val ROUND_TIMEOUT_MILLIS = TIMEOUT_MILLIS + HEDGE_DELAY_MILLIS
        const val MAX_ENDPOINTS = 4
        // Independent operators. No account data, credentials, redirects, or response logging.
        val DEFAULT_ENDPOINTS = listOf(
            "https://www.google.com/generate_204",
            "https://www.cloudflare.com/cdn-cgi/trace"
        )
    }
}

internal object InternetProbeConfiguration {
    @Volatile var endpoints: List<String> = InternetProbe.DEFAULT_ENDPOINTS
        private set

    fun setEndpoints(endpoints: List<String>) {
        this.endpoints = validate(endpoints)
    }

    fun validate(endpoints: List<String>): List<String> {
        require(endpoints.size in 1..InternetProbe.MAX_ENDPOINTS) { "One to four Internet check endpoints are required" }
        endpoints.forEach { endpoint ->
            val uri = try { URI(endpoint) } catch (_: URISyntaxException) {
                throw IllegalArgumentException("Invalid Internet check endpoint")
            }
            require(endpoint.length <= 2048 && uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null && uri.rawFragment == null && uri.rawQuery == null &&
                (uri.port == -1 || uri.port in 1..65535)) {
                "Internet check endpoints must use HTTPS without credentials, query, or fragment"
            }
        }
        return Collections.unmodifiableList(endpoints.distinct())
    }
}
