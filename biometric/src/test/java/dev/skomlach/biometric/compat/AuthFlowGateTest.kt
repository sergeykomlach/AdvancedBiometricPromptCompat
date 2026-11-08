package dev.skomlach.biometric.compat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AuthFlowGateTest {

    @Test
    fun statusReadRunsBeforeMainDeliveryAndDropsReplacedFlow() {
        val background = mutableListOf<Runnable>()
        val main = mutableListOf<Runnable>()
        var active = true
        var reads = 0
        var delivered: Int? = null
        readOwnedAuthStatus(background::add, main::add, { active }, { ++reads }) {
            delivered = it.getOrThrow()
        }

        assertEquals(0, reads)
        assertTrue(main.isEmpty())
        background.single().run()
        assertEquals(1, reads)
        assertEquals(null, delivered)
        active = false
        main.single().run()
        assertEquals(null, delivered)
    }

    @Test
    fun statusFailureReachesTheOwningFlow() {
        val main = mutableListOf<Runnable>()
        val failure = IllegalStateException("probe failed")
        var delivered: Throwable? = null
        readOwnedAuthStatus(Runnable::run, main::add, { true }, { throw failure }) {
            delivered = it.exceptionOrNull()
        }

        main.single().run()
        assertTrue(delivered === failure)
    }

    @Test
    fun flowReplacementDoesNotWaitForSlowRouteReadOrCacheItsResult() {
        val cache = AuthFlowRouteCache<String, String?>(serializeCalculation = false)
        val reading = CountDownLatch(1)
        val allowResult = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            cache.beginFlow(1)
            val old = workers.submit<String?> {
                cache.getOrPut("fingerprint") {
                    reading.countDown()
                    check(allowResult.await(5, TimeUnit.SECONDS))
                    "old route"
                }
            }
            assertTrue(reading.await(5, TimeUnit.SECONDS))
            workers.submit {
                cache.endFlow(1)
                cache.beginFlow(2)
            }.get(2, TimeUnit.SECONDS)
            allowResult.countDown()
            assertEquals("old route", old.get(5, TimeUnit.SECONDS))
            assertEquals(null, cache.getOrPut("fingerprint") { null })
            assertEquals(null, cache.getOrPut("fingerprint") { "unexpected route" })
        } finally {
            allowResult.countDown()
            workers.shutdownNow()
        }
    }

    @Test
    fun tryStartAuthFlowAllowsOnlyFirstCaller() {
        val inProgress = AtomicBoolean(false)

        assertTrue(inProgress.tryStartAuthFlow())
        assertFalse(inProgress.tryStartAuthFlow())
    }

    @Test
    fun canceledAuthFlowTokenCannotContinue() {
        assertFalse(
            isAuthFlowActive(
                expectedGeneration = 7,
                currentGeneration = 7,
                inProgress = true,
                canceled = true
            )
        )
    }

    @Test
    fun replacedAuthFlowRejectsOldTokenAndAcceptsCurrentToken() {
        assertFalse(
            isAuthFlowActive(
                expectedGeneration = 7,
                currentGeneration = 8,
                inProgress = true,
                canceled = false
            )
        )
        assertTrue(
            isAuthFlowActive(
                expectedGeneration = 8,
                currentGeneration = 8,
                inProgress = true,
                canceled = false
            )
        )
    }

    @Test
    fun onlyCurrentAuthFlowCanReleaseSharedGate() {
        val inProgress = AtomicBoolean(true)

        assertFalse(
            inProgress.finishAuthFlowIfCurrent(
                expectedGeneration = 7,
                currentGeneration = 8
            )
        )
        assertTrue(inProgress.get())

        assertTrue(
            inProgress.finishAuthFlowIfCurrent(
                expectedGeneration = 8,
                currentGeneration = 8
            )
        )
        assertFalse(inProgress.get())
    }

    @Test
    fun routeCacheReusesValuesOnlyInsideCurrentFlowAndInvalidatesExplicitly() {
        val cache = AuthFlowRouteCache<String, String?>()
        var calculations = 0
        val calculate = {
            calculations++
            "route"
        }

        assertTrue(cache.getOrPut("face", calculate) === "route")
        assertTrue(cache.getOrPut("face", calculate) === "route")
        assertTrue(calculations == 2)

        cache.beginFlow(7)
        cache.getOrPut("face", calculate)
        cache.getOrPut("face", calculate)
        assertTrue(calculations == 3)

        cache.invalidate()
        cache.getOrPut("face", calculate)
        assertTrue(calculations == 4)
        cache.endFlow(7)
    }

    @Test
    fun systemEnrollChecksVolatileStateTwiceButPreparesModulesOnlyAfterSystemUi() {
        val beforeSystemUiCalls = mutableListOf<String>()
        runAuthPreflightStages(
            shouldPrepareModules = { false },
            checkPermissions = { next -> beforeSystemUiCalls += "permissions"; next() },
            prepareModulesTask = { next -> beforeSystemUiCalls += "prepare"; next() },
            checkSensor = { next -> beforeSystemUiCalls += "sensor"; next() },
            authenticate = { beforeSystemUiCalls += "system-ui" }
        )
        assertEquals(listOf("permissions", "sensor", "system-ui"), beforeSystemUiCalls)

        val afterSystemUiCalls = mutableListOf<String>()
        runAuthPreflightStages(
            shouldPrepareModules = { true },
            checkPermissions = { next -> afterSystemUiCalls += "permissions"; next() },
            prepareModulesTask = { next -> afterSystemUiCalls += "prepare"; next() },
            checkSensor = { next -> afterSystemUiCalls += "sensor"; next() },
            authenticate = { afterSystemUiCalls += "authenticate" }
        )
        assertEquals(
            listOf("permissions", "sensor", "prepare", "authenticate"),
            afterSystemUiCalls
        )
    }

    @Test
    fun terminalCallbackRunsAfterFlowIsReleased() {
        val calls = mutableListOf<String>()

        val dispatched = dispatchAfterFlowFinished(
            finishFlow = { calls += "finish"; true },
            dispatch = { calls += "callback" }
        )

        assertTrue(dispatched)
        assertEquals(listOf("finish", "callback"), calls)
    }

    @Test
    fun staleFlowDoesNotDispatchTerminalCallback() {
        var callbackCalled = false

        val dispatched = dispatchAfterFlowFinished(
            finishFlow = { false },
            dispatch = { callbackCalled = true }
        )

        assertFalse(dispatched)
        assertFalse(callbackCalled)
    }
}
