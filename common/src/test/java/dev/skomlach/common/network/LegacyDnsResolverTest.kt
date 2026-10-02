package dev.skomlach.common.network

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LegacyDnsResolverTest {
    private val address = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    @Test(timeout = 5_000)
    fun aNewRouteCanResolveWhileFourOldNativeQueriesIgnoreCancellation() {
        val oldStarted = CountDownLatch(4)
        val release = CountDownLatch(1)
        val resolver = LegacyDnsResolver<String>({ network, _ ->
            if (network == "old") { oldStarted.countDown(); ignoreInterrupts(release) }
            listOf(address)
        }, { runnable -> Thread(runnable).apply { isDaemon = true } })
        val old = List(4) { CancellableDns(resolver.lookup("old")) }
        val waiters = old.map { dns -> FutureTask { runCatching { dns.lookup("example.test") } }
            .also { Thread(it).apply { isDaemon = true }.start() } }
        try {
            assertTrue(oldStarted.await(1, TimeUnit.SECONDS))
            old.forEach { it.cancel() }
            waiters.forEach { assertTrue(it.get(1, TimeUnit.SECONDS).isFailure) }
            assertThrows(UnknownHostException::class.java) {
                CancellableDns(resolver.lookup("old")).lookup("example.test")
            }
            assertEquals(listOf(address), CancellableDns(resolver.lookup("new")).lookup("example.test"))
        } finally { release.countDown(); old.forEach { it.cancel() }; resolver.close() }
    }

    @Test(timeout = 5_000)
    fun repeatedRetriesCannotAllocateMoreThanEightNativeWorkers() {
        val started = CountDownLatch(8)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val resolver = LegacyDnsResolver<String>({ _, _ ->
            calls.incrementAndGet(); started.countDown(); ignoreInterrupts(release)
            listOf(address)
        }, { runnable -> Thread(runnable).apply { isDaemon = true } })
        val pending = List(8) { index -> CancellableDns(resolver.lookup("route-${index / 4}")) }
        pending.forEach { dns -> Thread { runCatching { dns.lookup("example.test") } }.apply { isDaemon = true }.start() }
        try {
            assertTrue(started.await(1, TimeUnit.SECONDS))
            pending.forEach { it.cancel() }
            repeat(20) { index ->
                assertThrows(UnknownHostException::class.java) {
                    CancellableDns(resolver.lookup("retry-$index")).lookup("example.test")
                }
            }
            assertEquals(8, calls.get())
        } finally { release.countDown(); pending.forEach { it.cancel() }; resolver.close() }
    }

    @Test(timeout = 5_000)
    fun twoStalledHostsCannotPreventTheThirdAndFourthFromResolvingOnTheSameRoute() {
        val stalledStarted = CountDownLatch(2)
        val release = CountDownLatch(1)
        val resolver = LegacyDnsResolver<String>({ _, host ->
            if (host.startsWith("stalled")) {
                stalledStarted.countDown()
                ignoreInterrupts(release)
            }
            listOf(address)
        }, { runnable -> Thread(runnable).apply { isDaemon = true } })
        val stalled = List(2) { CancellableDns(resolver.lookup("same-route")) }
        stalled.forEachIndexed { index, dns ->
            Thread { runCatching { dns.lookup("stalled-$index.example") } }
                .apply { isDaemon = true }.start()
        }
        try {
            assertTrue(stalledStarted.await(1, TimeUnit.SECONDS))
            for (host in listOf("third.example", "fourth.example")) {
                assertEquals(listOf(address), CancellableDns(resolver.lookup("same-route")).lookup(host))
            }
            assertEquals(1L, release.count)
        } finally {
            release.countDown()
            stalled.forEach { it.cancel() }
            resolver.close()
        }
    }

    private fun ignoreInterrupts(latch: CountDownLatch) {
        while (true) {
            try { latch.await(); return } catch (_: InterruptedException) { }
        }
    }
}
