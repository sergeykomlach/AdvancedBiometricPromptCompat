/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

import android.net.TrafficStats
import android.net.Network
import dev.skomlach.common.misc.ExecutorHelper
import kotlinx.coroutines.flow.StateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

object NetworkApi {
    fun isWebUrl(u: String): Boolean {
        var url = u
        if (url.isEmpty()) return false
        url = url.lowercase(Locale.ROOT)
        //Fix java.lang.RuntimeException: utext_close failed: U_REGEX_STACK_OVERFLOW
        val slash = url.indexOf("/")
        if (slash > 0 && slash < url.indexOf("?")) {
            url = url.substring(0, url.indexOf("?"))
        }
        return (url.startsWith("http://") || url.startsWith("https://")) && android.util.Patterns.WEB_URL.matcher(
            url
        ).matches()
    }

    /** Shared automatically checked Internet state, also used by hasInternet/listeners. */
    val networkState: StateFlow<NetworkState>
        get() = Connection.networkState

    /** Fast read/OS refresh; Internet checks run automatically in the background. */
    fun refreshNetworkState(): NetworkState = Connection.refreshAndGetState()

    /** Raw Android route metadata, independent of the active Internet verdict. */
    val osNetworkState: StateFlow<NetworkState> get() = Connection.osNetworkState

    /** Optional app-wide override for networks restricting the default public check hosts. */
    fun setInternetCheckEndpoints(endpoints: List<String>) {
        InternetProbeConfiguration.setEndpoints(endpoints)
        Connection.internetCheckConfigurationChanged()
    }

    /** Raw OS state for a selected Network, e.g. a bound socket or local-only LAN. */
    fun networkStateFor(network: Network): StateFlow<NetworkState> = Connection.stateForNetwork(network)

    private data class RoutedCheck(val states: StateFlow<NetworkState>, val checker: NetworkReachability)
    private val networkChecks = LinkedHashMap<Network, RoutedCheck>()

    private fun checkerFor(network: Network): NetworkReachability {
        val states = Connection.stateForNetwork(network)
        return synchronized(networkChecks) {
            val current = networkChecks[network]?.takeIf { it.states === states }
            val routed = current ?: RoutedCheck(states, NetworkReachability(states, {
                val refreshed = Connection.stateForNetwork(network)
                if (refreshed === states) refreshed.value else states.value
            }, ExecutorHelper.scope)).also { networkChecks[network] = it }
            if (networkChecks.size > 16) networkChecks.remove(networkChecks.keys.first())
            routed.checker
        }
    }

    private val reachability by lazy {
        NetworkReachability(Connection.osNetworkState, Connection::refreshAndGetOsState, ExecutorHelper.scope)
    }

    /**
     * Opt-in endpoint check, separate from OS connectivity and authentication policy.
     * Reuse a probe instance for request deduplication/cache reuse. The application's probe
     * must provide non-blocking start/cancel methods and use its normal HTTP/TLS policy.
     * HTTP 401/403/5xx remain REACHABLE with their status code; callers handle authorization.
     * Cancelling the last waiter, a timeout, or a route change cancels the underlying probe.
     */
    suspend fun probeReachability(
        endpoint: String,
        probe: EndpointProbe,
        timeoutMillis: Long = 5_000L,
        cacheMillis: Long = 10_000L
    ): ReachabilityResult = reachability.check(endpoint, probe, timeoutMillis, cacheMillis)

    /**
     * Check an explicitly routed request. The probe must use this same Network for sockets
     * and DNS; the library neither binds the process nor bypasses its VPN automatically.
     * A Network obtained from Android is unusable after its onLost event.
     */
    suspend fun probeReachability(
        endpoint: String,
        probe: EndpointProbe,
        network: Network,
        timeoutMillis: Long = 5_000L,
        cacheMillis: Long = 10_000L
    ): ReachabilityResult = checkerFor(network).check(endpoint, probe, timeoutMillis, cacheMillis)

    /**
     * Cached measured Internet availability. After shared monitor initialization, repeated
     * reads perform no Android service/DNS/socket I/O. Callbacks and watchdogs update it.
     * Wake, route changes and ongoing checks retain the last confirmed result.
     * Two failed/stalled checks confirm offline; one successful check confirms online.
     * Before the first conclusive verdict the Boolean result is false (CHECKING).
     */
    fun hasInternet(): Boolean {
        return Connection.isConnection
    }

    fun refreshConnectionState(): Boolean {
        return Connection.refreshAndGetConnection()
    }

    /** A route exists, including local-only/captive/blocked networks. Not an Internet signal. */
    fun hasNetworkTransport(): Boolean {
        return Connection.hasNetworkTransport()
    }

    @Throws(Exception::class)
    fun createConnection(link: String?, timeout: Int): HttpURLConnection {
        require(!link.isNullOrBlank()) { "URL is empty" }
        val url = URL(link).toURI().normalize().toURL()
        require(url.protocol.equals("http", ignoreCase = true) ||
            url.protocol.equals("https", ignoreCase = true)
        ) {
            "Only HTTP(S) URLs are supported"
        }
        val conn = if (url.protocol.equals(
                "https",
                ignoreCase = true
            )
        ) url.openConnection() as HttpsURLConnection else url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = timeout
        conn.readTimeout = timeout
        TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())
        return conn
    }

    @Throws(IOException::class)
    fun fastCopy(src: InputStream, dest: OutputStream) {
        if (src is FileInputStream && dest is FileOutputStream) {
            val inChannel = src.channel
            val outChannel = dest.channel
            val size = inChannel.size()
            var position: Long = 0
            while (position < size) {
                position += inChannel.transferTo(position, size - position, outChannel)
            }
            return
        }
        val size = src.available().takeIf { it > 0 } ?: 0
        val buffer = ByteArray(size.coerceIn(MIN_COPY_BUFFER_SIZE, MAX_COPY_BUFFER_SIZE))
        var bytesRead: Int
        while (src.read(buffer).also { bytesRead = it } != -1) {
            dest.write(buffer, 0, bytesRead)
        }
    }

    fun resolveUrl(baseUrl: String?, relativeUrl: String): String {
        try {
            return URI(baseUrl).resolve(relativeUrl).toString()
        } catch (ignore: Throwable) {
        }
        return relativeUrl
    }

    private const val MIN_COPY_BUFFER_SIZE = 8 * 1024
    private const val MAX_COPY_BUFFER_SIZE = 64 * 1024
}
