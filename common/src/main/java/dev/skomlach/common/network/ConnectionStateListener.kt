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

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.os.Build
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ConnectionStateListener {
    private val registry = NetworkRouteRegistry<Network> { it.networkHandle }
    private val lifecycleLock = Any()
    private val listenersStarted = AtomicBoolean(false)
    private val session = AtomicLong()
    private val refreshSequence = AtomicLong()
    private val allNetworksRegistered = AtomicBoolean(false)
    private val defaultRegistered = AtomicBoolean(false)
    private val registeredCallbacks = mutableListOf<NetworkCallback>()
    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?

    internal val networkState: StateFlow<NetworkState> get() = registry.states
    internal fun currentNetwork(state: NetworkState): Network? = registry.effectiveNetworkFor(state.networkId)
    private val watchdog = Runnable { refreshAsync(forceMetadata = true) }

    private fun callback(epoch: Long, tracksDefault: Boolean): NetworkCallback = object : NetworkCallback() {
        private fun active() = listenersStarted.get() && session.get() == epoch
        private fun metadata() = !tracksDefault || !allNetworksRegistered.get()
        private fun refreshDefaultIfNeeded() {
            if (active() && !tracksDefault && !defaultRegistered.get()) refreshAsync()
        }

        override fun onAvailable(network: Network) {
            if (active()) {
                if (tracksDefault) registry.defaultAvailable(network, epoch)
                else registry.available(network, epoch)
            }
            refreshDefaultIfNeeded()
        }

        override fun onLost(network: Network) {
            if (active()) {
                if (tracksDefault) registry.defaultLost(network, epoch)
                else registry.lost(network, epoch)
            }
            refreshDefaultIfNeeded()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (active() && metadata()) registry.capabilities(network, describe(network, capabilities), epoch)
            refreshDefaultIfNeeded()
        }

        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            if (active() && metadata()) registry.linkProperties(network, properties.toString(), epoch)
            refreshDefaultIfNeeded()
        }

        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            if (active() && metadata()) registry.blocked(network, blocked, epoch)
            refreshDefaultIfNeeded()
        }

    }

    private fun describe(network: Network, caps: NetworkCapabilities): NetworkRoute = NetworkRoute(
        networkId = network.networkHandle,
        hasInternetCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        isCaptivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
        isSuspended = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED),
        // Public constants are inlined; hasTransport returns false for unsupported values.
        transports = (0..NetworkCapabilities.TRANSPORT_SATELLITE).filter { caps.hasTransport(it) }.toSet()
    )

    @Suppress("DEPRECATION")
    private fun seed(network: Network, force: Boolean = false, epoch: Long = session.get()) {
        val revision = registry.snapshotRevision(network, force) ?: return
        var legacySuspended: Boolean? = null
        var legacyBlocked: Boolean? = null
        val route = try {
            // Legacy SDKs have no public suspension/blocked callback for this Network.
            val info = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) cm?.getNetworkInfo(network) else null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                legacySuspended = info?.state?.let { it == NetworkInfo.State.SUSPENDED }
            }
            legacyBlocked = info?.detailedState?.let { it == NetworkInfo.DetailedState.BLOCKED }
            cm?.getNetworkCapabilities(network)?.let {
                describe(network, it).copy(linkProperties = cm.getLinkProperties(network)?.toString())
            } ?: NetworkRoute(networkId = network.networkHandle, isKnown = false)
        } catch (e: Exception) {
            LogCat.log("ConnectionStateListener metadata unavailable:${e.javaClass.simpleName}")
            NetworkRoute(networkId = network.networkHandle, isKnown = false)
        }
        // The registry checks epoch and revision atomically with applying the snapshot.
        registry.seed(network, route, revision, epoch, legacySuspended, legacyBlocked)
    }

    internal fun refreshState(invalidate: Boolean = false, forceMetadata: Boolean = false): NetworkState {
        val epoch = session.get()
        if (!listenersStarted.get()) return registry.states.value
        val sequence = refreshSequence.incrementAndGet()
        val revision = registry.routingRevision
        try {
            val manager = cm
            if (manager == null) {
                registry.unknown(invalidate, epoch, sequence, revision)
                return registry.states.value
            }
            val bound = manager.boundNetworkForProcess
            val default = manager.activeNetwork
            val refreshMetadata = forceMetadata || !allNetworksRegistered.get() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            bound?.let { seed(it, refreshMetadata, epoch) }
            (default ?: registry.observedDefaultForRefresh())?.takeUnless { it == bound }?.let {
                seed(it, refreshMetadata, epoch)
            }
            registry.refreshRouting(bound, default, revision, invalidate, epoch, sequence)
        } catch (e: Exception) {
            LogCat.log("ConnectionStateListener refresh unavailable:${e.javaClass.simpleName}")
            registry.unknown(invalidate, epoch, sequence, revision)
        }
        return registry.states.value
    }

    internal fun stateFor(network: Network): StateFlow<NetworkState> {
        if (listenersStarted.get()) {
            seed(network, force = !allNetworksRegistered.get() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        }
        return registry.stateFor(network)
    }

    private fun refreshAsync(forceMetadata: Boolean = false) {
        val epoch = session.get()
        if (!listenersStarted.get()) return
        ExecutorHelper.scope.launch {
            if (!listenersStarted.get() || session.get() != epoch) return@launch
            refreshState(forceMetadata = forceMetadata)
            scheduleWatchdog()
        }
    }

    private fun scheduleWatchdog() {
        synchronized(lifecycleLock) {
            if (!listenersStarted.get()) return
            val state = registry.states.value
            val delay = when {
                state.hasNetworkTransport && !state.isConnected -> 2_000L
                !state.hasNetworkTransport -> 10_000L
                else -> 30_000L
            }
            ExecutorHelper.handler.removeCallbacks(watchdog)
            ExecutorHelper.handler.postDelayed(watchdog, delay)
        }
    }

    internal fun startListeners() {
        synchronized(lifecycleLock) {
            if (!listenersStarted.compareAndSet(false, true)) return
            val epoch = session.incrementAndGet()
            registry.activate(epoch)
            val all = callback(epoch, tracksDefault = false)
            try {
                val request = NetworkRequest.Builder().clearCapabilities().build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    cm?.registerNetworkCallback(request, all, ExecutorHelper.handler)
                } else cm?.registerNetworkCallback(request, all)
                if (cm != null) {
                    registeredCallbacks.add(all)
                    allNetworksRegistered.set(true)
                }
            } catch (e: Exception) {
                LogCat.log("ConnectionStateListener callback unavailable:${e.javaClass.simpleName}")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val default = callback(epoch, tracksDefault = true)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        cm?.registerDefaultNetworkCallback(default, ExecutorHelper.handler)
                    } else cm?.registerDefaultNetworkCallback(default)
                    if (cm != null) {
                        registeredCallbacks.add(default)
                        defaultRegistered.set(true)
                    }
                } catch (e: Exception) {
                    LogCat.log("ConnectionStateListener default callback unavailable:${e.javaClass.simpleName}")
                }
            }
        }
        refreshState(forceMetadata = true)
        scheduleWatchdog()
    }

    internal fun stopListeners() {
        val (callbacks, publish) = synchronized(lifecycleLock) {
            listenersStarted.set(false)
            val epoch = session.incrementAndGet()
            ExecutorHelper.handler.removeCallbacks(watchdog)
            val oldCallbacks = registeredCallbacks.toList()
            registeredCallbacks.clear()
            allNetworksRegistered.set(false)
            defaultRegistered.set(false)
            oldCallbacks to registry.reset(epoch, defer = true)
        }
        publish()
        callbacks.forEach { try { cm?.unregisterNetworkCallback(it) } catch (_: Exception) { } }
    }

    // Wake starts a fresh check; the public verdict stays unchanged until confirmation.
    internal fun onScreenStateChanged() {
        val epoch = session.get()
        if (listenersStarted.get()) registry.invalidate(epoch)
        refreshAsync(forceMetadata = true)
    }

    internal fun hasNetworkTransport(): Boolean = refreshState().hasNetworkTransport
}
