package dev.skomlach.common.network

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class NetworkRouteRegistryTest {
    private fun registry() = NetworkRouteRegistry<Int> { it.toLong() }
    private fun online(id: Int, vpn: Boolean = false) = NetworkRoute(
        id.toLong(), hasInternetCapability = true, isValidated = true, isVpn = vpn
    )

    @Test fun localBoundNetworkDoesNotBorrowDefaultInternetStatus() {
        val r = registry()
        r.capabilities(1, online(1))
        r.capabilities(2, NetworkRoute(2))
        r.refreshRouting(2, 1, r.routingRevision)
        assertEquals(NetworkAvailability.LOCAL_ONLY, r.states.value.availability)
        assertTrue(r.states.value.hasNetworkTransport)
        assertFalse(r.states.value.hasInternetCapability)
        assertFalse(r.states.value.isConnected)
    }

    @Test fun blockedDefaultIsRetainedWhenActiveNetworkReturnsNull() {
        val r = registry()
        r.defaultAvailable(1)
        r.capabilities(1, online(1))
        r.blocked(1, true)
        r.refreshRouting(null, null, r.routingRevision)
        assertEquals(NetworkAvailability.BLOCKED, r.states.value.availability)
        assertEquals(1L, r.states.value.networkId)
        assertTrue(r.states.value.hasNetworkTransport)
        assertFalse(r.states.value.isConnected)
    }

    @Test fun delayedBlockedEventRestoresTheObservedDefaultAfterNullPoll() {
        val r = registry()
        r.defaultAvailable(1)
        r.capabilities(1, online(1))
        r.refreshRouting(null, null, r.routingRevision)
        assertEquals(NetworkAvailability.UNAVAILABLE, r.states.value.availability)
        r.blocked(1, true)
        assertEquals(NetworkAvailability.BLOCKED, r.states.value.availability)
    }

    @Test fun losingBlockedDefaultDoesNotSelectOtherValidatedWifi() {
        val r = registry()
        r.capabilities(1, online(1))
        r.defaultAvailable(2)
        r.capabilities(2, online(2, vpn = true))
        r.blocked(2, true)
        r.defaultLost(2)
        r.refreshRouting(null, null, r.routingRevision)
        assertEquals(NetworkAvailability.UNAVAILABLE, r.states.value.availability)
        assertFalse(r.states.value.hasNetworkTransport)
    }

    @Test fun oldDefaultLostEventCannotClearItsReplacementOrPhysicalWifi() {
        val r = registry()
        r.defaultAvailable(1)
        r.capabilities(1, online(1))
        val wifi = r.stateFor(1)
        r.defaultAvailable(2)
        r.capabilities(2, online(2, vpn = true))
        r.defaultLost(1)
        assertEquals(2L, r.states.value.networkId)
        assertTrue(wifi.value.isConnected)
    }

    @Test fun stalePollingMetadataCannotOverwriteNewCallbackData() {
        val r = registry()
        val revision = r.snapshotRevision(1)!!
        r.capabilities(1, online(1))
        r.linkProperties(1, "new-dns")
        val current = r.stateFor(1).value
        r.seed(1, NetworkRoute(1, linkProperties = "old-dns"), revision)
        assertEquals(current, r.stateFor(1).value)
    }

    @Test fun staleDefaultPollCannotUndoAnOrderedRouteCallback() {
        val r = registry()
        r.defaultAvailable(1)
        r.capabilities(1, online(1))
        val revision = r.routingRevision
        r.defaultAvailable(2)
        r.capabilities(2, online(2, vpn = true))
        r.refreshRouting(null, 1, revision)
        assertEquals(2L, r.states.value.networkId)
    }

    @Test fun changingProcessBindingSelectsAndMonitorsTheBoundNetwork() {
        val r = registry()
        r.defaultAvailable(1)
        r.capabilities(1, online(1))
        r.capabilities(2, online(2, vpn = true))
        r.refreshRouting(2, 1, r.routingRevision)
        r.blocked(2, true)
        assertEquals(NetworkAvailability.BLOCKED, r.states.value.availability)
        r.refreshRouting(null, 1, r.routingRevision)
        assertEquals(1L, r.states.value.networkId)
        assertTrue(r.states.value.isConnected)
    }

    @Test fun selectedNetworkLossIsTerminalUntilAnAvailableEvent() {
        val r = registry()
        r.capabilities(1, online(1))
        r.refreshRouting(1, null, r.routingRevision)
        val route = r.stateFor(1)
        r.lost(1)
        r.capabilities(1, online(1))
        assertEquals(NetworkAvailability.UNAVAILABLE, route.value.availability)
        assertFalse(route.value.hasNetworkTransport)
        r.available(1)
        r.capabilities(1, online(1))
        assertTrue(route.value.isConnected)
    }

    @Test fun lostBoundNetworkCannotBeResurrectedByHistoryEviction() {
        val r = registry()
        r.capabilities(1, online(1))
        r.refreshRouting(1, null, r.routingRevision)
        r.lost(1)
        for (id in 2..100) { r.capabilities(id, online(id)); r.lost(id) }
        r.refreshRouting(1, null, r.routingRevision)
        assertEquals(NetworkAvailability.UNAVAILABLE, r.states.value.availability)
        assertFalse(r.states.value.hasNetworkTransport)
    }

    @Test fun sameVpnIdIsInvalidatedWhenItsPossiblePhysicalRouteChanges() {
        val r = registry()
        r.capabilities(1, online(1))
        r.defaultAvailable(2)
        r.capabilities(2, online(2, vpn = true))
        val vpn = r.states.value
        r.linkProperties(1, "new-address-and-dns")
        assertEquals(vpn.networkId, r.states.value.networkId)
        assertTrue(r.states.value.generation > vpn.generation)
    }

    @Test fun unrelatedNetworkCannotReplaceTheEffectiveRoute() {
        val r = registry()
        r.defaultAvailable(1)
        r.capabilities(1, online(1))
        val current = r.states.value
        r.capabilities(2, online(2))
        r.blocked(2, true)
        assertEquals(current, r.states.value)
        assertEquals(NetworkAvailability.BLOCKED, r.stateFor(2).value.availability)
    }

    @Test fun rapidWifiVpnWifiTransitionStillInvalidatesOldProbes() {
        val r = registry()
        r.capabilities(1, online(1))
        r.capabilities(2, online(2, vpn = true))
        r.defaultAvailable(1)
        val initial = r.states.value
        r.defaultAvailable(2)
        r.defaultAvailable(1)
        assertEquals(initial.networkId, r.states.value.networkId)
        assertTrue(r.states.value.generation >= initial.generation + 2)
    }

    @Test fun screenWakeInvalidatesBothProcessAndExplicitNetworkCaches() {
        val r = registry()
        r.defaultAvailable(1)
        r.capabilities(1, online(1))
        val explicit = r.stateFor(1)
        val processGeneration = r.states.value.generation
        val explicitGeneration = explicit.value.generation
        r.invalidate()
        assertTrue(r.states.value.generation > processGeneration)
        assertTrue(explicit.value.generation > explicitGeneration)
    }

    @Test fun legacySuspensionSurvivesCapabilitiesUntilAFreshResumeSnapshot() {
        val r = registry()
        r.defaultAvailable(1)
        r.capabilities(1, online(1))
        r.seed(1, online(1), r.snapshotRevision(1, force = true)!!, legacySuspended = true)
        r.capabilities(1, online(1))
        assertEquals(NetworkAvailability.SUSPENDED, r.states.value.availability)
        val revision = r.snapshotRevision(1, force = true)!!
        r.seed(1, online(1), revision)
        assertEquals(NetworkAvailability.SUSPENDED, r.states.value.availability)
        r.seed(1, online(1), r.snapshotRevision(1, force = true)!!, legacySuspended = false)
        assertTrue(r.states.value.isConnected)
    }

    @Test fun resetInvalidatesAllActiveObservations() {
        val r = registry()
        r.defaultAvailable(1)
        r.capabilities(1, online(1))
        val explicit = r.stateFor(1)
        r.reset()
        assertEquals(NetworkAvailability.UNKNOWN, r.states.value.availability)
        assertFalse(explicit.value.hasNetworkTransport)
    }

    @Test fun unchangedCallbackStillRejectsAnOlderPollingSnapshot() {
        val r = registry()
        r.capabilities(1, online(1))
        val before = r.stateFor(1).value
        val revision = r.snapshotRevision(1, force = true)!!
        r.capabilities(1, online(1))
        r.seed(1, NetworkRoute(1), revision)
        assertEquals(before, r.stateFor(1).value)
    }

    @Test fun olderParallelPollCannotRollbackProcessBinding() {
        val r = registry()
        r.capabilities(1, online(1))
        r.capabilities(2, online(2))
        val revision = r.routingRevision
        r.refreshRouting(2, 1, revision, sequence = 2)
        r.refreshRouting(1, 1, revision, sequence = 1)
        assertEquals(2L, r.states.value.networkId)
    }

    @Test fun olderFailedPollCannotClearANewerSuccessfulPollOrCallback() {
        val r = registry()
        r.capabilities(1, online(1))
        val revision = r.routingRevision
        r.refreshRouting(null, 1, revision, sequence = 2)
        val current = r.states.value
        r.unknown(sequence = 1, expectedRevision = revision)
        assertEquals(current, r.states.value)
        r.defaultAvailable(2)
        r.capabilities(2, online(2))
        r.unknown(sequence = 3, expectedRevision = revision)
        assertEquals(2L, r.states.value.networkId)
        assertTrue(r.states.value.isConnected)
    }

    @Test fun blockedDefaultCanBeTrackedWithoutADefaultCallback() {
        val r = registry()
        r.capabilities(1, online(1))
        r.refreshRouting(null, 1, r.routingRevision)
        r.blocked(1, true)
        r.refreshRouting(null, null, r.routingRevision)
        assertEquals(NetworkAvailability.BLOCKED, r.states.value.availability)
    }

    @Test fun legacyBlockedSnapshotRestoresDefaultWhenActiveNetworkIsNull() {
        val r = registry()
        r.defaultAvailable(1)
        r.capabilities(1, online(1))
        r.refreshRouting(null, null, r.routingRevision)
        assertEquals(1, r.observedDefaultForRefresh())
        r.seed(1, online(1), r.snapshotRevision(1, force = true)!!, legacyBlocked = true)
        r.refreshRouting(null, null, r.routingRevision)
        assertEquals(NetworkAvailability.BLOCKED, r.states.value.availability)
        r.seed(1, online(1), r.snapshotRevision(1, force = true)!!, legacyBlocked = false)
        assertTrue(r.states.value.isConnected)
        r.lost(1)
        assertNull(r.observedDefaultForRefresh())
    }

    @Test fun explicitVpnIsInvalidatedWhenItsPhysicalRouteChanges() {
        val r = registry()
        r.defaultAvailable(1)
        r.capabilities(1, online(1))
        r.capabilities(2, online(2, vpn = true))
        val explicit = r.stateFor(2)
        val generation = explicit.value.generation
        r.linkProperties(1, "changed-dns")
        assertTrue(explicit.value.generation > generation)
    }

    @Test fun explicitRouteDoesNotFollowAnUnrelatedDefaultSwitch() {
        val r = registry()
        r.defaultAvailable(1)
        r.capabilities(1, online(1))
        val explicit = r.stateFor(1)
        val original = explicit.value
        r.defaultAvailable(2)
        r.capabilities(2, online(2, vpn = true))
        assertEquals(original, explicit.value)
        assertEquals(2L, r.states.value.networkId)
    }

    @Test fun callbacksFromStoppedObservationCannotRepopulateState() {
        val r = registry()
        r.activate(1)
        r.defaultAvailable(1, epoch = 1)
        r.capabilities(1, online(1), epoch = 1)
        r.reset(epoch = 2)
        val stopped = r.states.value
        r.defaultAvailable(2, epoch = 1)
        r.capabilities(2, online(2), epoch = 1)
        r.refreshRouting(2, 2, r.routingRevision, epoch = 1)
        assertEquals(stopped, r.states.value)
    }

    @Test fun delayedStopPublicationCannotOverwriteRestartedObservation() {
        val r = registry()
        r.activate(1)
        r.defaultAvailable(1, epoch = 1)
        r.capabilities(1, online(1), epoch = 1)
        val explicit = r.stateFor(1)
        val stopPublication = r.reset(epoch = 2, defer = true)
        r.activate(3)
        r.defaultAvailable(1, epoch = 3)
        r.capabilities(1, online(1), epoch = 3)
        val restarted = r.states.value
        stopPublication()
        r.lost(1, epoch = 1)
        assertEquals(restarted, r.states.value)
        assertTrue(explicit.value.isConnected)
    }

    @Test fun collectorCanUpdateRegistryFromAnotherThreadWithoutHeldLocks() = runBlocking {
        val r = registry()
        val observer = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            r.states.drop(1).take(1).collect {
                val update = FutureTask { r.invalidate(); r.stateFor(1).value }
                Thread(update).apply { isDaemon = true }.start()
                assertTrue(update.get(2, TimeUnit.SECONDS).generation > 0)
            }
        }
        r.defaultAvailable(1)
        observer.join()
    }
}
