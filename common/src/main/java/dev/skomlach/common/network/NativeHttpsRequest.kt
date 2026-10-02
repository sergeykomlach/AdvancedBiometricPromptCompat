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

import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import javax.net.ssl.SSLException

/** Only sends a public GET and reads its status line; no cookies, auth, redirects or pooling. */
internal class NativeHttpsRequest(
    private val endpoint: String,
    private val sockets: SocketFactory,
    private val dns: CancellableDns,
    private val secureSocket: (Socket, String, Int, Int) -> Socket,
    private val proxy: Proxy = Proxy.NO_PROXY,
    timeoutMillis: Long = InternetProbe.TIMEOUT_MILLIS,
    private val nanoTime: () -> Long = System::nanoTime,
    private val proxySelector: ProxySelector? = null,
    private val deadlines: ScheduledExecutorService = requestDeadlines
) : ProbeCancellation {
    private val cancelled = AtomicBoolean(false)
    private val socket = AtomicReference<Socket?>(null)
    private val deadline = nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    private var routeDeadline = deadline

    private class RouteAttempt {
        private val expired = AtomicBoolean(false)
        private val socket = AtomicReference<Socket?>(null)
        fun install(current: Socket) {
            socket.set(current)
            if (expired.get()) close()
        }
        fun close() {
            expired.set(true)
            try { socket.getAndSet(null)?.close() } catch (_: IOException) { }
        }
    }

    fun execute(): ProbeResponse {
        val uri = URI(endpoint).toASCIIString().let(::URI)
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port in 1..65535))
        val host = uri.host.removePrefix("[").removeSuffix("]")
        val port = if (uri.port == -1) 443 else uri.port
        try {
            val candidates = (proxySelector?.select(uri) ?: listOf(proxy))
                .filter { it.type() == Proxy.Type.DIRECT || it.type() == Proxy.Type.HTTP }
                .distinct()
            if (candidates.isEmpty()) throw IOException("Unsupported proxy configuration")
            var failure: IOException? = null
            candidates.forEachIndexed { index, candidate ->
                // Reserve time for PAC alternatives instead of giving a dead first proxy
                // the entire request deadline. No DIRECT route is added to the PAC result.
                val budget = (remainingMillis(deadline) / (candidates.size - index)).coerceAtLeast(1)
                routeDeadline = nanoTime() + TimeUnit.MILLISECONDS.toNanos(budget.toLong())
                val attempt = RouteAttempt()
                // SO_TIMEOUT bounds individual TLS reads, not an entire trickling handshake.
                // Capture this attempt's socket so a late timer cannot close the next route.
                val abort = deadlines.schedule({ attempt.close() }, budget.toLong(), TimeUnit.MILLISECONDS)
                try {
                    return executeRoute(uri, host, port, candidate, attempt)
                } catch (e: IOException) {
                    if (cancelled.get() || Thread.currentThread().isInterrupted) throw e
                    failure = e
                    if (candidate.type() == Proxy.Type.HTTP && e !is SSLException) {
                        try { proxySelector?.connectFailed(uri, candidate.address(), e) }
                        catch (_: RuntimeException) { }
                    }
                } finally {
                    abort.cancel(false)
                    attempt.close()
                }
            }
            throw failure ?: IOException("No usable proxy route")
        } finally {
            cancel()
        }
    }

    private fun executeRoute(uri: URI, host: String, port: Int, candidate: Proxy, attempt: RouteAttempt): ProbeResponse {
        var raw: Socket? = null
        var tls: Socket? = null
        try {
            val target = when (candidate.type()) {
                Proxy.Type.DIRECT -> InetSocketAddress.createUnresolved(host, port)
                Proxy.Type.HTTP -> candidate.address() as? InetSocketAddress
                    ?: throw IOException("Invalid HTTP proxy")
                else -> throw IOException("Unsupported proxy type")
            }
            raw = connect(target, attempt)
            if (candidate.type() == Proxy.Type.HTTP) tunnel(raw, authority(host, port))
            remainingMillis()
            // The Android adapter verifies the original hostname, even through CONNECT.
            tls = secureSocket(raw, host, port, remainingMillis())
            val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
            val request = "GET $path HTTP/1.1\r\nHost: ${authority(host, port)}\r\n" +
                "User-Agent: BiometricCompat-Connectivity\r\nCache-Control: no-cache\r\n" +
                "Pragma: no-cache\r\nConnection: close\r\n\r\n"
            tls.outputStream.write(request.toByteArray(StandardCharsets.US_ASCII))
            val status = readStatus(tls)
            remainingMillis()
            return ProbeResponse.Http(status)
        } finally {
            // Close the plain transport first: SSL close_notify must not extend the deadline.
            socket.compareAndSet(raw, null)
            try { raw?.close() } catch (_: IOException) { }
            try { tls?.close() } catch (_: IOException) { }
        }
    }

    private fun connect(target: InetSocketAddress, attempt: RouteAttempt): Socket {
        remainingMillis()
        // A supplied resolved proxy address must not be re-resolved. IP literals are
        // parsed locally: DnsResolver.query() sends DNS queries even for numeric hosts.
        val resolved = target.address ?: numericAddress(target.hostString)
        val addresses = addressCandidates(resolved?.let(::listOf)
            ?: dns.lookup(target.hostString, remainingMillis().toLong()))
        var failure: IOException? = null
        addresses.forEachIndexed { index, address ->
            remainingMillis()
            val current = sockets.createSocket()
            socket.set(current)
            attempt.install(current)
            try {
                val budget = (remainingMillis() / (addresses.size - index)).coerceAtLeast(1)
                current.connect(InetSocketAddress(address, target.port), budget)
                return current
            } catch (e: IOException) {
                failure = e
                socket.compareAndSet(current, null)
                try { current.close() } catch (_: IOException) { }
            }
        }
        throw failure ?: IOException("No resolved addresses")
    }

    private fun tunnel(raw: Socket, authority: String) {
        raw.outputStream.write(("CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n\r\n")
            .toByteArray(StandardCharsets.US_ASCII))
        if (readStatus(raw) != 200) throw IOException("HTTP proxy refused CONNECT")
        var bytes = 0
        val input = raw.inputStream
        while (true) {
            val line = readLine(raw, input)
            bytes += line.length + 2
            if (bytes > MAX_PROXY_HEADERS) throw IOException("Oversized proxy response")
            if (line.isEmpty()) return
        }
    }

    private fun readStatus(current: Socket): Int {
        val line = readLine(current, current.inputStream)
        val parts = line.split(' ', limit = 3)
        if (parts.size < 2 || parts[0] !in listOf("HTTP/1.0", "HTTP/1.1") ||
            parts[1].length != 3 || parts[1].any { it !in '0'..'9' }) {
            throw IOException("Invalid HTTP status line")
        }
        return parts[1].toInt().takeIf { it in 100..599 }
            ?: throw IOException("Invalid HTTP status code")
    }

    private fun readLine(current: Socket, input: InputStream): String {
        val line = StringBuilder()
        while (line.length < MAX_LINE_BYTES) {
            current.soTimeout = remainingMillis()
            val value = input.read()
            if (value == -1) throw IOException("Incomplete HTTP response")
            if (value == '\n'.code) {
                if (line.isEmpty() || line.last() != '\r') throw IOException("Invalid HTTP line ending")
                line.setLength(line.length - 1)
                return line.toString()
            }
            if (value !in 32..126 && value != '\r'.code && value != '\t'.code) {
                throw IOException("Invalid HTTP response character")
            }
            line.append(value.toChar())
        }
        throw IOException("Oversized HTTP response line")
    }

    private fun remainingMillis(limit: Long = routeDeadline): Int {
        if (cancelled.get()) throw InterruptedIOException("Internet check cancelled")
        val now = nanoTime()
        val nanos = minOf(deadline - now, limit - now)
        if (nanos <= 0) throw SocketTimeoutException("Internet check deadline exceeded")
        return TimeUnit.NANOSECONDS.toMillis(nanos).coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override fun cancel() {
        cancelled.set(true)
        dns.cancel()
        try { socket.getAndSet(null)?.close() } catch (_: IOException) { }
    }

    private fun authority(host: String, port: Int): String =
        (if (host.contains(':')) "[$host]" else host) + ":$port"

    companion object {
        private val requestDeadlines by lazy {
            ScheduledThreadPoolExecutor(1) { runnable ->
                Thread(runnable, "BiometricCompat-native-deadline").apply { isDaemon = true }
            }.apply { removeOnCancelPolicy = true }
        }
        private const val MAX_ADDRESSES = 4
        private const val MAX_LINE_BYTES = 4_096
        private const val MAX_PROXY_HEADERS = 16_384

        private fun addressCandidates(addresses: List<InetAddress>): List<InetAddress> {
            val distinct = addresses.distinct()
            val firstSize = distinct.firstOrNull()?.address?.size
            val first = distinct.filter { it.address.size == firstSize }.iterator()
            val second = distinct.filter { it.address.size != firstSize }.iterator()
            return buildList {
                while (size < MAX_ADDRESSES && (first.hasNext() || second.hasNext())) {
                    if (first.hasNext()) add(first.next())
                    if (size < MAX_ADDRESSES && second.hasNext()) add(second.next())
                }
            }
        }

        private fun ipv4Bytes(host: String): ByteArray? {
            val parts = host.split('.')
            if (parts.size != 4) return null
            val values = parts.map { part ->
                if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }) return null
                part.toInt().takeIf { it in 0..255 } ?: return null
            }
            return values.map { it.toByte() }.toByteArray()
        }

        /** No name service calls, including for malformed IPv6 literals. */
        private fun numericAddress(host: String): InetAddress? {
            ipv4Bytes(host)?.let { return InetAddress.getByAddress(it) }
            if (!host.contains(':')) return null
            val compression = host.indexOf("::")
            if (compression != host.lastIndexOf("::")) throw UnknownHostException("Invalid IPv6 address")
            val tokens = host.split(':')
            val groups = mutableListOf<Int>()
            for ((index, token) in tokens.withIndex()) {
                if (token.isEmpty()) continue
                if (token.contains('.')) {
                    if (index != tokens.lastIndex) throw UnknownHostException("Invalid IPv6 address")
                    val bytes = ipv4Bytes(token) ?: throw UnknownHostException("Invalid IPv6 address")
                    groups += ((bytes[0].toInt() and 255) shl 8) or (bytes[1].toInt() and 255)
                    groups += ((bytes[2].toInt() and 255) shl 8) or (bytes[3].toInt() and 255)
                } else {
                    if (token.length > 4 || token.any { it.digitToIntOrNull(16) == null })
                        throw UnknownHostException("Invalid IPv6 address")
                    groups += token.toInt(16)
                }
            }
            if (compression < 0 && (groups.size != 8 || tokens.any { it.isEmpty() }) ||
                compression >= 0 && (groups.size >= 8 || host.startsWith(':') && !host.startsWith("::") ||
                    host.endsWith(':') && !host.endsWith("::"))) throw UnknownHostException("Invalid IPv6 address")
            val before = if (compression < 0) groups.size else host.substring(0, compression)
                .split(':').count { it.isNotEmpty() }
            val expanded = groups.take(before) + List(8 - groups.size) { 0 } + groups.drop(before)
            val bytes = ByteArray(16)
            expanded.forEachIndexed { index, value ->
                bytes[index * 2] = (value shr 8).toByte()
                bytes[index * 2 + 1] = value.toByte()
            }
            return InetAddress.getByAddress(bytes)
        }
    }
}
