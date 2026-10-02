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

import kotlinx.coroutines.flow.StateFlow

/** Ordered callback data. Flow publication never runs while the registry lock is held. */
internal class NetworkRouteRegistry<N>(private val id: (N) -> Long) {
    private class Entry(val tracker: NetworkStateTracker, var route: NetworkRoute,
        var revision: Long = 0, var legacySuspended: Boolean? = null)
    private val lock = Any()
    private val entries = LinkedHashMap<N, Entry>()
    private val process = NetworkStateTracker()
    private val pending = LinkedHashMap<NetworkStateTracker, NetworkState>()
    private var bound: N? = null
    private var default: N? = null
    private var observedDefault: N? = null
    private var observationEpoch = 0L
    private var observing = true
    private var latestRefreshSequence = Long.MIN_VALUE
    @Volatile var routingRevision: Long = 0
        private set
    val states: StateFlow<NetworkState> = process.states

    private fun entry(network: N): Entry = entries.getOrPut(network) {
        val route = NetworkRoute(networkId = id(network), isKnown = false)
        Entry(NetworkStateTracker(route), route)
    }

    private fun accepts(epoch: Long?) = epoch == null || (observing && epoch == observationEpoch)

    private fun acceptRefresh(sequence: Long?): Boolean {
        if (sequence == null) return true
        if (sequence <= latestRefreshSequence) return false
        latestRefreshSequence = sequence
        return true
    }

    fun activate(epoch: Long) = synchronized(lock) { observationEpoch = epoch; observing = true }

    private fun record(tracker: NetworkStateTracker, route: NetworkRoute, invalidate: Boolean = false) {
        tracker.reduce(route, invalidate)?.let { pending[tracker] = it }
    }

    private fun drain(): () -> Unit {
        val updates = pending.toList()
        pending.clear()
        return { updates.forEach { (tracker, state) -> tracker.publish(state) } }
    }

    private fun mutate(epoch: Long? = null, action: () -> Unit) {
        val publish = synchronized(lock) {
            if (accepts(epoch)) action()
            drain()
        }
        publish()
    }

    fun stateFor(network: N): StateFlow<NetworkState> = synchronized(lock) { entry(network).tracker.states }

    fun snapshotRevision(network: N, force: Boolean = false): Long? = synchronized(lock) {
        val e = entry(network)
        e.revision.takeIf { e.route.isAvailable && (force || !e.route.isKnown) }
    }

    fun observedDefaultForRefresh(): N? = synchronized(lock) {
        observedDefault?.takeIf { entries[it]?.route?.isAvailable == true }
    }

    fun effectiveNetworkFor(networkId: Long?): N? = synchronized(lock) {
        (bound ?: default)?.takeIf { id(it) == networkId && entries[it]?.route?.isAvailable == true }
    }

    fun seed(network: N, route: NetworkRoute, expectedRevision: Long, epoch: Long? = null,
        legacySuspended: Boolean? = null, legacyBlocked: Boolean? = null) = mutate(epoch) {
        val e = entry(network)
        if (e.revision == expectedRevision && e.route.isAvailable) {
            if (legacySuspended != null) e.legacySuspended = legacySuspended
            change(network, route.copy(isBlocked = legacyBlocked ?: e.route.isBlocked,
                isSuspended = e.legacySuspended ?: route.isSuspended))
        }
    }

    fun refreshRouting(bound: N?, activeDefault: N?, expectedRevision: Long,
        invalidate: Boolean = false, epoch: Long? = null, sequence: Long? = null) = mutate(epoch) {
        if (!acceptRefresh(sequence)) return@mutate
        this.bound = bound
        if (routingRevision == expectedRevision) {
            // activeNetwork is null for a blocked default. Never substitute another network.
            default = activeDefault ?: observedDefault?.takeIf { entries[it]?.route?.isBlocked == true }
            if (activeDefault != null) observedDefault = activeDefault
        }
        publishProcess(invalidate)
    }

    fun defaultAvailable(network: N, epoch: Long? = null) = mutate(epoch) {
        availableInside(network)
        observedDefault = network
        default = network
        routingRevision++
        publishProcess()
    }

    fun defaultLost(network: N, epoch: Long? = null) = mutate(epoch) {
        if (observedDefault == network) observedDefault = null
        if (default == network) { default = null; routingRevision++; publishProcess() }
    }

    private fun availableInside(network: N) {
        val e = entry(network)
        if (!e.route.isAvailable) {
            e.legacySuspended = null
            change(network, NetworkRoute(networkId = id(network), isKnown = false))
        }
    }

    fun available(network: N, epoch: Long? = null) = mutate(epoch) { availableInside(network) }

    fun capabilities(network: N, route: NetworkRoute, epoch: Long? = null) = mutate(epoch) {
        val e = entry(network)
        if (e.route.isAvailable) change(network, route.copy(linkProperties = e.route.linkProperties,
            isBlocked = e.route.isBlocked, isSuspended = e.legacySuspended ?: route.isSuspended))
    }

    fun linkProperties(network: N, value: String, epoch: Long? = null) = mutate(epoch) {
        val e = entry(network)
        if (e.route.isAvailable) change(network, e.route.copy(linkProperties = value))
    }

    fun blocked(network: N, value: Boolean, epoch: Long? = null) = mutate(epoch) {
        val e = entry(network)
        if (e.route.isAvailable) {
            if (value && default == null && observedDefault == network) default = network
            change(network, e.route.copy(isBlocked = value))
        }
    }

    fun lost(network: N, epoch: Long? = null) = mutate(epoch) {
        val e = entry(network)
        if (default == network) { default = null; routingRevision++ }
        if (observedDefault == network) observedDefault = null
        change(network, e.route.copy(isAvailable = false))
        val lost = entries.filter { (key, value) ->
            !value.route.isAvailable && key != bound && key != default && key != observedDefault
        }.keys
        lost.take((lost.size - MAX_LOST_ENTRIES).coerceAtLeast(0)).forEach { entries.remove(it) }
    }

    fun unknown(invalidate: Boolean = false, epoch: Long? = null, sequence: Long? = null,
        expectedRevision: Long? = null) = mutate(epoch) {
        if (!acceptRefresh(sequence) || (expectedRevision != null && expectedRevision != routingRevision)) return@mutate
        record(process, NetworkRoute(isKnown = false), invalidate)
    }

    /** With defer=true, the owner publishes after releasing its registration/lifecycle lock. */
    fun reset(epoch: Long? = null, defer: Boolean = false): () -> Unit {
        val publish = synchronized(lock) {
            if (epoch != null) { observationEpoch = epoch; observing = false }
            bound = null; default = null; observedDefault = null; routingRevision++
            latestRefreshSequence = Long.MIN_VALUE
            entries.values.forEach {
                it.legacySuspended = null
                it.route = it.route.copy(isKnown = false, isAvailable = false)
                it.revision++
                record(it.tracker, it.route, invalidate = true)
            }
            record(process, NetworkRoute(isKnown = false), invalidate = true)
            drain()
        }
        if (!defer) publish()
        return publish
    }

    fun invalidate(epoch: Long? = null) = mutate(epoch) {
        entries.values.forEach { record(it.tracker, it.route, invalidate = true) }
        publishProcess(invalidate = true)
    }

    private fun change(network: N, next: NetworkRoute) {
        val e = entry(network)
        val previous = e.route
        // Identical callback data still supersedes an older in-flight polling snapshot.
        e.revision++
        if (previous == next) return
        e.route = next
        record(e.tracker, next)
        if (!next.isVpn && (previous.hasInternetCapability || next.hasInternetCapability)) {
            entries.forEach { (key, value) ->
                if (key != network && value.route.isVpn && value.route.isAvailable) {
                    record(value.tracker, value.route, invalidate = true)
                }
            }
        }
        val selected = bound ?: default
        val selectedRoute = selected?.let { entries[it]?.route }
        // Public SDK redacts underlying VPN networks. Invalidate possible dependencies.
        val vpnDependencyChanged = selected != network && selectedRoute?.isVpn == true &&
            !next.isVpn && (previous.hasInternetCapability || next.hasInternetCapability)
        publishProcess(vpnDependencyChanged)
    }

    private fun publishProcess(invalidate: Boolean = false) {
        val network = bound ?: default
        record(process, network?.let { entry(it).route } ?: NetworkRoute(), invalidate)
    }

    companion object { private const val MAX_LOST_ENTRIES = 32 }
}
