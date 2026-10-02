package dev.skomlach.common.network

import org.junit.Assert.*
import org.junit.Test

class NetworkStateTest {
    @Test
    fun measuredVerdictSurvivesTemporaryTransportChangesWhileRawStateRemainsInstantaneous() {
        for (availability in listOf(NetworkAvailability.UNKNOWN, NetworkAvailability.UNAVAILABLE,
            NetworkAvailability.BLOCKED, NetworkAvailability.SUSPENDED, NetworkAvailability.LOCAL_ONLY)) {
            val raw = NetworkState(availability)
            assertFalse(raw.isConnected)
            assertTrue(raw.copy(internetAccess = InternetAccess.AVAILABLE).isConnected)
            assertFalse(raw.copy(internetAccess = InternetAccess.UNAVAILABLE).isConnected)
        }
    }

    @Test
    fun initialStateIsUnknownRatherThanOptimisticallyOnline() {
        val state = NetworkStateTracker().states.value
        assertEquals(NetworkAvailability.UNKNOWN, state.availability)
        assertFalse(state.isConnected)
    }

    @Test
    fun unvalidatedDefaultVpnIsNotMaskedByValidatedWifi() {
        val registry = NetworkRouteRegistry<Int> { it.toLong() }
        registry.capabilities(1, NetworkRoute(1, hasInternetCapability = true, isValidated = true))
        registry.capabilities(2, NetworkRoute(2, hasInternetCapability = true, isVpn = true))
        registry.defaultAvailable(2)
        assertEquals(2L, registry.states.value.networkId)
        assertEquals(NetworkAvailability.UNVALIDATED, registry.states.value.availability)
        assertTrue(registry.states.value.isVpn)
        assertFalse(registry.states.value.isConnected)
        assertTrue(registry.states.value.hasNetworkTransport)
    }

    @Test
    fun boundVpnTakesPrecedenceOverValidatedDefaultWifi() {
        val registry = NetworkRouteRegistry<Int> { it.toLong() }
        registry.capabilities(1, NetworkRoute(1, hasInternetCapability = true, isValidated = true))
        registry.capabilities(2, NetworkRoute(2, hasInternetCapability = true, isVpn = true))
        registry.refreshRouting(2, 1, registry.routingRevision)
        assertEquals(2L, registry.states.value.networkId)
        assertFalse(registry.states.value.isConnected)
    }

    @Test
    fun missingDefaultDoesNotFallBackToAnotherValidatedNetwork() {
        val registry = NetworkRouteRegistry<Int> { it.toLong() }
        registry.capabilities(1, NetworkRoute(1, hasInternetCapability = true, isValidated = true))
        registry.refreshRouting(null, null, registry.routingRevision)
        assertEquals(NetworkAvailability.UNAVAILABLE, registry.states.value.availability)
        assertFalse(registry.states.value.hasNetworkTransport)
    }

    @Test
    fun unavailableCapabilitiesAreUnknownInsteadOfConfirmedOffline() {
        val tracker = NetworkStateTracker()
        tracker.update(NetworkRoute(2, isKnown = false))
        assertEquals(NetworkAvailability.UNKNOWN, tracker.states.value.availability)
        assertFalse(tracker.states.value.isConnected)
    }

    @Test
    fun blockedValidatedNetworkIsNotUsable() {
        val tracker = NetworkStateTracker()
        tracker.update(NetworkRoute(2, hasInternetCapability = true, isValidated = true, isBlocked = true))
        assertEquals(NetworkAvailability.BLOCKED, tracker.states.value.availability)
        assertFalse(tracker.states.value.isConnected)
        assertTrue(tracker.states.value.hasNetworkTransport)
    }

    @Test
    fun validatedRouteReplacementStillProducesANewGeneration() {
        val tracker = NetworkStateTracker()
        tracker.update(NetworkRoute(1, hasInternetCapability = true, isValidated = true))
        val wifi = tracker.states.value
        val vpn = tracker.update(NetworkRoute(2, hasInternetCapability = true, isValidated = true, isVpn = true))!!
        assertTrue(wifi.isConnected)
        assertTrue(vpn.isConnected)
        assertTrue(vpn.generation > wifi.generation)
        assertEquals(vpn, tracker.states.value)
    }

    @Test
    fun linkChangeAndScreenWakeInvalidateExistingProbeResults() {
        val tracker = NetworkStateTracker()
        val route = NetworkRoute(1, hasInternetCapability = true, isValidated = true, linkProperties = "dns-a")
        tracker.update(route)
        val original = tracker.states.value.generation
        val changed = tracker.update(route.copy(linkProperties = "dns-b"))!!
        assertTrue(changed.generation > original)
        val wake = tracker.update(route.copy(linkProperties = "dns-b"), invalidate = true)!!
        assertTrue(wake.generation > changed.generation)
    }

    @Test
    fun repeatedPollingKeepsTheGenerationAndDoesNotEmitAChange() {
        val tracker = NetworkStateTracker()
        val route = NetworkRoute(1, hasInternetCapability = true, isValidated = true)
        tracker.update(route)
        val first = tracker.states.value
        assertNull(tracker.update(route))
        assertEquals(first, tracker.states.value)
    }

    @Test
    fun localOnlyRouteHasTransportButDoesNotClaimInternet() {
        val tracker = NetworkStateTracker()
        tracker.update(NetworkRoute(1, transports = setOf(1)))
        val state = tracker.states.value
        assertEquals(NetworkAvailability.LOCAL_ONLY, state.availability)
        assertTrue(state.hasNetworkTransport)
        assertFalse(state.hasInternetCapability)
        assertFalse(state.isConnected)
    }

    @Test
    fun captiveAndSuspendedFlagsTakePrecedenceOverStaleValidation() {
        val tracker = NetworkStateTracker()
        val route = NetworkRoute(1, hasInternetCapability = true, isValidated = true)
        tracker.update(route.copy(isCaptivePortal = true))
        assertEquals(NetworkAvailability.CAPTIVE_PORTAL, tracker.states.value.availability)
        assertFalse(tracker.states.value.isConnected)
        tracker.update(route.copy(isSuspended = true))
        assertEquals(NetworkAvailability.SUSPENDED, tracker.states.value.availability)
        assertFalse(tracker.states.value.isConnected)
    }

    @Test
    fun vpnTransportHandoverChangesGenerationWithoutChangingNetworkId() {
        val tracker = NetworkStateTracker()
        val vpn = NetworkRoute(1, hasInternetCapability = true, isValidated = true, isVpn = true, transports = setOf(1, 4))
        tracker.update(vpn)
        val first = tracker.states.value
        tracker.update(vpn.copy(transports = setOf(0, 4)))
        assertEquals(first.networkId, tracker.states.value.networkId)
        assertTrue(tracker.states.value.generation > first.generation)
        assertEquals(setOf(0, 4), tracker.states.value.transports)
    }

    @Test
    fun unknownCapabilitiesStillRetainKnownTransportPresence() {
        val tracker = NetworkStateTracker()
        tracker.update(NetworkRoute(1, isKnown = false))
        assertTrue(tracker.states.value.hasNetworkTransport)
        assertFalse(tracker.states.value.isConnected)
    }

}
