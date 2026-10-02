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
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal fun interface AsyncDnsLookup {
    fun start(host: String, complete: (List<InetAddress>?, IOException?) -> Unit): ProbeCancellation
}

/** Bounds the calling HTTP worker even when a platform resolver cannot be interrupted. */
internal class CancellableDns(private val lookup: AsyncDnsLookup, private val timeoutMillis: Long = 2_000) {
    private class Pending {
        val ready = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        private val finished = AtomicBoolean(false)
        private val handle = AtomicReference<ProbeCancellation?>(null)
        var addresses: List<InetAddress>? = null
        var error: IOException? = null

        fun complete(addresses: List<InetAddress>?, error: IOException?) {
            if (finished.compareAndSet(false, true)) {
                this.addresses = addresses?.toList()
                this.error = error
                ready.countDown()
            }
        }

        fun install(cancellation: ProbeCancellation) {
            if (!handle.compareAndSet(null, cancellation)) cancelSafely(cancellation)
        }

        fun cancel() {
            cancelled.set(true)
            finished.set(true)
            handle.getAndSet(CANCELLED)?.let { if (it !== CANCELLED) cancelSafely(it) }
            ready.countDown()
        }
    }

    private val cancelled = AtomicBoolean(false)
    private val pending = Collections.newSetFromMap(ConcurrentHashMap<Pending, Boolean>())

    fun lookup(hostname: String, maxWaitMillis: Long = timeoutMillis): List<InetAddress> {
        val query = Pending()
        pending.add(query)
        try {
            if (cancelled.get()) query.cancel()
            if (!query.cancelled.get()) query.install(lookup.start(hostname, query::complete))
            if (!query.ready.await(minOf(timeoutMillis, maxWaitMillis).coerceAtLeast(1), TimeUnit.MILLISECONDS)) {
                query.cancel()
                throw failure(SocketTimeoutException("DNS deadline exceeded"))
            }
            if (cancelled.get() || query.cancelled.get()) throw failure(InterruptedIOException("DNS cancelled"))
            query.error?.let { throw failure(it) }
            return query.addresses?.takeIf { it.isNotEmpty() } ?: throw UnknownHostException("Empty DNS answer")
        } catch (e: InterruptedException) {
            query.cancel()
            Thread.currentThread().interrupt()
            throw failure(InterruptedIOException("DNS interrupted"))
        } catch (e: RuntimeException) {
            query.cancel()
            throw failure(e)
        } catch (e: IOException) {
            query.cancel()
            throw if (e is UnknownHostException) e else failure(e)
        } finally {
            pending.remove(query)
        }
    }

    fun cancel() {
        cancelled.set(true)
        pending.forEach { it.cancel() }
    }

    private fun failure(cause: Exception) = UnknownHostException("DNS unavailable").apply { initCause(cause) }

    companion object {
        private val CANCELLED = ProbeCancellation { }
        private fun cancelSafely(cancellation: ProbeCancellation) {
            try { cancellation.cancel() } catch (_: Exception) { }
        }
    }
}
