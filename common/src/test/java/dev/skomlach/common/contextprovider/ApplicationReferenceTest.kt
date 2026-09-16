package dev.skomlach.common.contextprovider

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class ApplicationReferenceTest {
    @Test
    fun unavailableApplicationIsRetriedWithoutCachingNull() {
        var available: Any? = null
        var registrations = 0
        val reference = ApplicationReference({ available }, { registrations++ }, { throw it })
        assertNull(reference.getOrNull())
        assertEquals(0, registrations)

        val application = Any()
        available = application
        assertSame(application, reference.getOrNull())
        assertSame(application, reference.getOrNull())
        assertEquals(1, registrations)
    }

    @Test
    fun concurrentFirstAccessPublishesOneApplicationAndRegistersOnce() {
        val application = Any()
        val resolutions = AtomicInteger()
        val registrations = AtomicInteger()
        val start = CountDownLatch(1)
        val reference = ApplicationReference(
            { resolutions.incrementAndGet(); application },
            { registrations.incrementAndGet() },
            { throw it }
        )
        val pool = Executors.newFixedThreadPool(8)
        try {
            val calls = (1..8).map {
                pool.submit(Callable { start.await(); reference.getOrNull() })
            }
            start.countDown()
            calls.forEach { assertSame(application, it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, resolutions.get())
            assertEquals(1, registrations.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun cachedApplicationDoesNotDependOnLaterResolverAvailability() {
        val application = Any()
        var available: Any? = application
        val reference = ApplicationReference({ available }, {}, { throw it })
        assertSame(application, reference.getOrNull())
        available = null
        assertSame(application, reference.getOrNull())
    }

    @Test
    fun callbackFailureKeepsApplicationAvailableAndAllowsRetry() {
        val application = Any()
        var registrations = 0
        val errors = mutableListOf<Throwable>()
        val failure = IllegalStateException("registration failed")
        val reference = ApplicationReference(
            { application },
            { if (++registrations == 1) throw failure },
            { errors += it }
        )

        assertSame(application, reference.getOrNull())
        assertSame(application, reference.getOrNull())
        assertSame(application, reference.getOrNull())
        assertEquals(2, registrations)
        assertEquals(listOf(failure), errors)
    }

    @Test
    fun registrationCanReadApplicationWithoutRegisteringRecursively() {
        val application = Any()
        var registrations = 0
        lateinit var reference: ApplicationReference<Any>
        reference = ApplicationReference(
            { application },
            { registrations++; assertSame(application, reference.getOrNull()) },
            { throw it }
        )

        assertSame(application, reference.getOrNull())
        assertEquals(1, registrations)
    }

    @Test
    fun resolverFailureDoesNotPoisonLaterAccess() {
        val application = Any()
        var resolutions = 0
        val failure = IllegalStateException("not ready")
        val errors = mutableListOf<Throwable>()
        val reference = ApplicationReference(
            { if (++resolutions == 1) throw failure else application },
            {},
            { errors += it }
        )

        assertNull(reference.getOrNull())
        assertSame(application, reference.getOrNull())
        assertEquals(listOf(failure), errors)
    }
}
