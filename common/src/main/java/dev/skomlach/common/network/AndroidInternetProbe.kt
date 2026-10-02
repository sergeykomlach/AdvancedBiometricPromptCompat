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

import android.net.DnsResolver
import android.net.Network
import android.net.SSLCertificateSocketFactory
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import java.io.IOException
import java.net.ProxySelector
import java.net.Socket
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/** Native Android DNS and routed sockets, with platform TLS trust and hostname checks. */
internal class AndroidInternetProbe(private val networkFor: (NetworkState) -> Network?) : EndpointProbe {
    private val threadNumber = AtomicInteger()
    private fun thread(task: Runnable) = Thread(task, "BiometricCompat-internet-${threadNumber.incrementAndGet()}")
        .apply { isDaemon = true }
    private val httpExecutor = internetRequestExecutor(::thread)
    private val deadlines = ScheduledThreadPoolExecutor(1, ::thread).apply { removeOnCancelPolicy = true }
    private val legacyDns = LegacyDnsResolver<Network>({ network, host -> network.getAllByName(host).toList() }, ::thread)
    private val tlsFactory = SSLContext.getInstance("TLS").apply { init(null, null, null) }.socketFactory
    private val closed = AtomicBoolean(false)
    private val active = Collections.newSetFromMap(ConcurrentHashMap<Pending, Boolean>())

    private class Pending(val dns: CancellableDns) {
        val cancelled = AtomicBoolean(false)
        val completed = AtomicBoolean(false)
        val request = AtomicReference<NativeHttpsRequest?>(null)
        val task = AtomicReference<FutureTask<Unit>?>(null)
        val deadline = AtomicReference<ScheduledFuture<*>?>(null)
        fun cancel() {
            cancelled.set(true)
            completed.set(true)
            dns.cancel()
            request.get()?.cancel()
            task.get()?.cancel(true)
            deadline.get()?.cancel(false)
        }
    }

    override fun start(endpoint: String, network: NetworkState, complete: (ProbeResponse) -> Unit): ProbeCancellation {
        val selected = networkFor(network)
        if (selected == null || closed.get()) {
            complete(ProbeResponse.Unreachable)
            return ProbeCancellation { }
        }
        val dns = CancellableDns(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Api29Dns.lookup(selected)
        } else legacyDns.lookup(selected), timeoutMillis = 1_500)
        val pending = Pending(dns)
        active.add(pending)
        val task = object : FutureTask<Unit>({
            val response = try {
                if (pending.cancelled.get()) throw IOException("Internet check cancelled")
                // NativeHttpsRequest keeps the selector's ordered PAC alternatives and
                // the same routed socket factory/DNS for every attempt.
                val request = NativeHttpsRequest(endpoint, selected.socketFactory, dns, ::secureSocket,
                    proxySelector = ProxySelector.getDefault(), deadlines = deadlines)
                pending.request.set(request)
                if (pending.cancelled.get()) request.cancel()
                request.execute()
            } catch (_: SSLException) { ProbeResponse.TlsError }
              catch (_: Exception) { ProbeResponse.Unreachable }
            if (!pending.cancelled.get() && !closed.get() && pending.completed.compareAndSet(false, true)) complete(response)
            Unit
        }) {
            override fun done() {
                pending.deadline.get()?.cancel(false)
                active.remove(pending)
                httpExecutor.remove(this)
            }
        }
        pending.task.set(task)
        try {
            val deadline = deadlines.schedule({
                if (pending.completed.compareAndSet(false, true)) { pending.cancel(); complete(ProbeResponse.Unreachable) }
            },
                InternetProbe.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            pending.deadline.set(deadline)
            if (pending.cancelled.get() || task.isDone) deadline.cancel(false)
            if (closed.get()) pending.cancel() else httpExecutor.execute(task)
        } catch (_: RejectedExecutionException) {
            val report = pending.completed.compareAndSet(false, true)
            pending.cancel()
            if (report) complete(ProbeResponse.Unreachable)
        }
        return ProbeCancellation { pending.cancel() }
    }

    private fun secureSocket(raw: Socket, host: String, port: Int, timeout: Int): Socket {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // This factory's hostname overload performs trust + hostname verification on API 23.
            @Suppress("DEPRECATION")
            return SSLCertificateSocketFactory.getDefault(timeout, null).createSocket(raw, host, port, true)
        }
        val tls = tlsFactory.createSocket(raw, host, port, true) as SSLSocket
        try {
            tls.soTimeout = timeout
            Api24Tls.verifyHostname(tls)
            tls.startHandshake()
            return tls
        } catch (e: Exception) {
            // Abort the raw transport before closing SSL, which could send close_notify.
            try { raw.close() } catch (_: IOException) { }
            try { tls.close() } catch (_: IOException) { }
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private object Api24Tls {
        fun verifyHostname(socket: SSLSocket) {
            socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private object Api29Dns {
        private val callbacks = Executor { it.run() }
        fun lookup(network: Network) = AsyncDnsLookup { host, complete ->
            val cancellation = CancellationSignal()
            DnsResolver.getInstance().query(network, host,
                DnsResolver.FLAG_NO_CACHE_LOOKUP or DnsResolver.FLAG_NO_RETRY, callbacks, cancellation,
                object : DnsResolver.Callback<List<java.net.InetAddress>> {
                    override fun onAnswer(answer: List<java.net.InetAddress>, rcode: Int) {
                        if (rcode == 0) complete(answer, null)
                        else complete(null, UnknownHostException("DNS answer unavailable"))
                    }
                    override fun onError(error: DnsResolver.DnsException) {
                        complete(null, UnknownHostException("DNS query failed"))
                    }
                })
            ProbeCancellation { cancellation.cancel() }
        }
    }

    fun close() {
        closed.set(true)
        active.forEach { it.cancel() }
        deadlines.shutdownNow()
        httpExecutor.shutdownNow()
        legacyDns.close()
    }
}
