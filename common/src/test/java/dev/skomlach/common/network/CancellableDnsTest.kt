package dev.skomlach.common.network

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CancellableDnsTest {
    private val address = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    @Test fun returnsTheSelectedResolversAnswer() {
        val cancelled = AtomicInteger()
        val dns = CancellableDns(AsyncDnsLookup { _, complete ->
            complete(listOf(address), null)
            ProbeCancellation { cancelled.incrementAndGet() }
        })
        assertEquals(listOf(address), dns.lookup("example.invalid"))
        assertEquals(0, cancelled.get())
    }

    @Test(timeout = 5_000)
    fun stalledResolverHasABoundedWaitAndIsCancelled() {
        val cancelled = AtomicInteger()
        val dns = CancellableDns(AsyncDnsLookup { _, _ -> ProbeCancellation { cancelled.incrementAndGet() } }, 50)
        assertThrows(UnknownHostException::class.java) { dns.lookup("example.invalid") }
        assertEquals(1, cancelled.get())
    }

    @Test(timeout = 5_000)
    fun cancellationReleasesTheHttpWorkerImmediately() {
        val started = CountDownLatch(1)
        val cancelled = AtomicInteger()
        val dns = CancellableDns(AsyncDnsLookup { _, _ ->
            started.countDown()
            ProbeCancellation { cancelled.incrementAndGet() }
        })
        val pending = FutureTask { dns.lookup("example.invalid") }
        Thread(pending).apply { isDaemon = true }.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        dns.cancel()
        val error = assertThrows(ExecutionException::class.java) { pending.get(1, TimeUnit.SECONDS) }
        assertTrue(error.cause is UnknownHostException)
        assertEquals(1, cancelled.get())
    }

    @Test(timeout = 5_000)
    fun cancellationBeforeHandleInstallationCancelsTheLateHandle() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = AtomicInteger()
        val dns = CancellableDns(AsyncDnsLookup { _, _ ->
            entered.countDown()
            release.await()
            ProbeCancellation { cancelled.incrementAndGet() }
        })
        val pending = FutureTask { dns.lookup("example.invalid") }
        Thread(pending).apply { isDaemon = true }.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            dns.cancel()
        } finally { release.countDown() }
        assertThrows(ExecutionException::class.java) { pending.get(1, TimeUnit.SECONDS) }
        assertEquals(1, cancelled.get())
    }

    @Test fun cancellationBeforeLookupDoesNotStartAResolver() {
        val starts = AtomicInteger()
        val dns = CancellableDns(AsyncDnsLookup { _, _ -> starts.incrementAndGet(); ProbeCancellation { } })
        dns.cancel()
        assertThrows(UnknownHostException::class.java) { dns.lookup("example.invalid") }
        assertEquals(0, starts.get())
    }

    @Test fun emptyAnswerAndTransportFailureAreReportedAsDnsUnavailable() {
        for (lookup in listOf(
            AsyncDnsLookup { _, complete -> complete(emptyList(), null); ProbeCancellation { } },
            AsyncDnsLookup { _, complete -> complete(null, IOException("Resolver unavailable")); ProbeCancellation { } }
        )) {
            assertThrows(UnknownHostException::class.java) { CancellableDns(lookup).lookup("example.invalid") }
        }
    }

    @Test fun runtimeResolverFailureDoesNotEscapeTheHttpWorkerAsAnUncheckedException() {
        val dns = CancellableDns(AsyncDnsLookup { _, _ -> throw IllegalStateException("Resolver unavailable") })
        assertThrows(UnknownHostException::class.java) { dns.lookup("example.invalid") }
    }
}
