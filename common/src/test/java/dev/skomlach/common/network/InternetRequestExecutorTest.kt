package dev.skomlach.common.network

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class InternetRequestExecutorTest {
    private fun executor() = internetRequestExecutor { task -> Thread(task).apply { isDaemon = true } }

    @Test(timeout = 5_000)
    fun twoStalledEndpointsCannotStarveTheThirdAndFourth() {
        val blocked = CountDownLatch(1)
        val started = CountDownLatch(2)
        val healthy = CountDownLatch(2)
        val executor = executor()
        try {
            repeat(2) { executor.execute { started.countDown(); blocked.await() } }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            repeat(2) { executor.execute { healthy.countDown() } }
            assertTrue("Healthy endpoints were queued behind stalled transports", healthy.await(2, TimeUnit.SECONDS))
            assertEquals(1L, blocked.count)
        } finally { blocked.countDown(); executor.shutdownNow() }
    }

    @Test(timeout = 5_000)
    fun overloadIsBoundedInsteadOfGrowingAnUnboundedQueueOrThreadPool() {
        val blocked = CountDownLatch(1)
        val started = CountDownLatch(4)
        val executor = executor()
        try {
            repeat(4) { executor.execute { started.countDown(); blocked.await() } }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            repeat(4) { executor.execute { } }
            assertThrows(RejectedExecutionException::class.java) { executor.execute { } }
            assertEquals(4, executor.poolSize)
        } finally { blocked.countDown(); executor.shutdownNow() }
    }

    @Test(timeout = 5_000)
    fun idleWorkersAreReleasedAndTheNextCheckCanStartAgain() {
        val executor = executor()
        try {
            executor.setKeepAliveTime(50, TimeUnit.MILLISECONDS)
            val first = CountDownLatch(1)
            executor.execute { first.countDown() }
            assertTrue(first.await(2, TimeUnit.SECONDS))
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (executor.poolSize > 0 && System.nanoTime() < end) Thread.sleep(10)
            assertEquals(0, executor.poolSize)
            val next = CountDownLatch(1)
            executor.execute { next.countDown() }
            assertTrue(next.await(2, TimeUnit.SECONDS))
        } finally { executor.shutdownNow() }
    }
}
