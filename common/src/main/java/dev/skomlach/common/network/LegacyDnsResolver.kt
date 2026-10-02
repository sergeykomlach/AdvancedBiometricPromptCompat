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

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** All configured endpoints can resolve, with a full spare round for a replacement route. */
internal class LegacyDnsResolver<N>(
    private val resolve: (N, String) -> List<InetAddress>,
    threads: ThreadFactory
) {
    private val lock = Any()
    private val running = mutableMapOf<N, Int>()
    private val executor = ThreadPoolExecutor(0, MAX_WORKERS, 30, TimeUnit.SECONDS,
        SynchronousQueue(), threads, ThreadPoolExecutor.AbortPolicy())

    fun lookup(network: N): AsyncDnsLookup = AsyncDnsLookup { host, complete ->
        val admitted = synchronized(lock) {
            val count = running[network] ?: 0
            if (count >= MAX_PER_ROUTE) false else {
                running[network] = count + 1
                true
            }
        }
        if (!admitted) {
            complete(null, UnknownHostException("DNS resolver busy"))
            ProbeCancellation { }
        } else {
            try {
                val task = object : FutureTask<Unit>({
                    try { complete(resolve(network, host), null) }
                    catch (_: Exception) { complete(null, UnknownHostException("DNS unavailable")) }
                    Unit
                }) {
                    override fun run() {
                        try { super.run() } finally { release(network) }
                    }
                }
                executor.execute(task)
                // Android's native getAllByName may ignore interruption. Accounting is released
                // by the worker's finally, never by Future.cancel(), so retries stay bounded.
                ProbeCancellation { task.cancel(true) }
            } catch (_: RejectedExecutionException) {
                release(network)
                complete(null, UnknownHostException("DNS resolver busy"))
                ProbeCancellation { }
            }
        }
    }

    private fun release(network: N) = synchronized(lock) {
        val remaining = (running[network] ?: 1) - 1
        if (remaining == 0) running.remove(network) else running[network] = remaining
    }

    fun close() { executor.shutdownNow() }

    private companion object {
        const val MAX_PER_ROUTE = InternetProbe.MAX_ENDPOINTS
        const val MAX_WORKERS = 2 * MAX_PER_ROUTE
    }
}
