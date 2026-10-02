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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** OS route information. InternetAccess adds the library's automatic reachability verdict. */
enum class NetworkAvailability {
    UNKNOWN, UNAVAILABLE, UNVALIDATED, VALIDATED, BLOCKED, LOCAL_ONLY, CAPTIVE_PORTAL, SUSPENDED
}

/** CHECKING is used before the first verdict; later checks retain AVAILABLE/UNAVAILABLE. */
enum class InternetAccess { NOT_CHECKED, CHECKING, AVAILABLE, UNAVAILABLE }

data class NetworkState(
    val availability: NetworkAvailability = NetworkAvailability.UNKNOWN,
    val networkId: Long? = null,
    val isVpn: Boolean = false,
    /** Changes with route metadata or a confirmed Internet verdict change. */
    val generation: Long = 0,
    val hasInternetCapability: Boolean = availability == NetworkAvailability.VALIDATED ||
        availability == NetworkAvailability.UNVALIDATED || availability == NetworkAvailability.CAPTIVE_PORTAL,
    /** Public Android transport constants; a VPN can report several transports. */
    val transports: Set<Int> = emptySet(),
    /** NOT_CHECKED is used only by raw OS snapshots. Connection publishes measured status. */
    val internetAccess: InternetAccess = InternetAccess.NOT_CHECKED
) {
    val isConnected: Boolean
        get() = when (internetAccess) {
            InternetAccess.NOT_CHECKED -> availability == NetworkAvailability.VALIDATED
            // This is the last confirmed verdict, including during handover/debounce.
            // Raw OS snapshots retain their separate instantaneous transport semantics.
            InternetAccess.AVAILABLE -> true
            else -> false
        }

    val hasNetworkTransport: Boolean
        get() = networkId != null && availability != NetworkAvailability.UNAVAILABLE
}

internal data class NetworkRoute(
    val networkId: Long? = null,
    val hasInternetCapability: Boolean = false,
    val isValidated: Boolean = false,
    val isVpn: Boolean = false,
    val isBlocked: Boolean = false,
    val linkProperties: String? = null,
    val isKnown: Boolean = true,
    val isAvailable: Boolean = true,
    val isCaptivePortal: Boolean = false,
    val isSuspended: Boolean = false,
    val transports: Set<Int> = emptySet()
) {
    val availability: NetworkAvailability
        get() = when {
            networkId == null -> if (isKnown) NetworkAvailability.UNAVAILABLE else NetworkAvailability.UNKNOWN
            !isAvailable -> NetworkAvailability.UNAVAILABLE
            isBlocked -> NetworkAvailability.BLOCKED
            !isKnown -> NetworkAvailability.UNKNOWN
            isSuspended -> NetworkAvailability.SUSPENDED
            isCaptivePortal -> NetworkAvailability.CAPTIVE_PORTAL
            !hasInternetCapability -> NetworkAvailability.LOCAL_ONLY
            isValidated -> NetworkAvailability.VALIDATED
            else -> NetworkAvailability.UNVALIDATED
        }
}

internal class NetworkStateTracker(initialRoute: NetworkRoute? = null) {
    private var snapshot = initialRoute?.let { snapshot(it, 1) } ?: NetworkState()
    private val mutableState = MutableStateFlow(snapshot)
    val states: StateFlow<NetworkState> = mutableState.asStateFlow()
    private var route: NetworkRoute? = initialRoute

    @Synchronized
    fun reduce(nextRoute: NetworkRoute, invalidate: Boolean = false): NetworkState? {
        if (route == nextRoute && !invalidate) return null
        route = nextRoute
        snapshot = snapshot(nextRoute, snapshot.generation + 1)
        return snapshot
    }

    fun publish(state: NetworkState) {
        mutableState.update { current -> if (state.generation > current.generation) state else current }
    }

    fun update(nextRoute: NetworkRoute, invalidate: Boolean = false): NetworkState? {
        val next = reduce(nextRoute, invalidate)
        if (next != null) publish(next)
        return next
    }

    private fun snapshot(route: NetworkRoute, generation: Long) = NetworkState(
        route.availability, route.networkId, route.isVpn, generation, route.hasInternetCapability,
        java.util.Collections.unmodifiableSet(route.transports.toSet())
    )
}
