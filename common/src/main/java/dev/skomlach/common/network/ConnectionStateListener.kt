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
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ConnectionStateListener {
    private data class ConnectionState(
        val hasTransport: Boolean,
        val isValidated: Boolean
    )

    private val isConnectionOk = AtomicBoolean(true)
    private val hasNetworkTransport = AtomicBoolean(false)
    private val listenersStarted = AtomicBoolean(false)
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: NetworkCallback? = null
    private val connectionWatchdog = Runnable {
        ExecutorHelper.scope.launch {
            refreshConnectionState()
            scheduleConnectionWatchdog()
        }
    }

    companion object {
        private const val TRANSITION_RECHECK_DELAY_MS = 2_000L
        private const val OFFLINE_RECHECK_DELAY_MS = 10_000L
        private const val STABLE_RECHECK_DELAY_MS = 30_000L
    }

    init {
        connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        //Just an initial state; Will be checked properly later
        ExecutorHelper.scope.launch {
            refreshConnectionStateAndSchedule()
        }

        networkCallback = object : NetworkCallback() {
            override fun onUnavailable() {
                LogCat.logError("ConnectionStateListener onUnavailable")
                ExecutorHelper.scope.launch {
                    refreshConnectionStateAndSchedule()
                }
            }

            override fun onAvailable(network: Network) {
                LogCat.logError("ConnectionStateListener onAvailable")
                ExecutorHelper.scope.launch {
                    refreshConnectionStateAndSchedule()
                }
                delayedRefreshConnectionState(1_000)
                delayedRefreshConnectionState(3_000)
            }

            override fun onLost(network: Network) {
                LogCat.logError("ConnectionStateListener onLost")
                ExecutorHelper.scope.launch {
                    refreshConnectionStateAndSchedule()
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                LogCat.logError("ConnectionStateListener onCapabilitiesChanged")
                ExecutorHelper.scope.launch {
                    refreshConnectionStateAndSchedule()
                }
            }
        }

    }

    private fun delayedRefreshConnectionState(delay: Long) {
        ExecutorHelper.handler.postDelayed({
            ExecutorHelper.scope.launch {
                refreshConnectionStateAndSchedule()
            }
        }, delay)
    }

    private fun refreshConnectionStateAndSchedule(): Boolean {
        val connected = refreshConnectionState()
        scheduleConnectionWatchdog()
        return connected
    }

    internal fun refreshConnectionState(): Boolean {
        val state = detectConnectionState()
        hasNetworkTransport.set(state.hasTransport)
        LogCat.log(
            "ConnectionStateListener refreshConnectionState - ${state.isValidated}, transport - ${state.hasTransport}"
        )
        setState(state.isValidated)
        return state.isValidated
    }

    internal fun hasNetworkTransport(): Boolean {
        refreshConnectionState()
        return hasNetworkTransport.get()
    }

    @Suppress("DEPRECATION")
    private fun detectConnectionState(): ConnectionState {
        return try {
            val cm = connectivityManager ?: return ConnectionState(
                hasTransport = false,
                isValidated = false
            )
            var hasTransport = false
            var isValidated = false
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    hasTransport = true
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        isValidated = true
                        break
                    }
                }
            }
            ConnectionState(hasTransport, isValidated)
        } catch (_: Throwable) {
            ConnectionState(
                hasTransport = false,
                isValidated = false
            )
        }
    }

    private fun scheduleConnectionWatchdog(delay: Long = getNextWatchdogDelay()) {
        if (!listenersStarted.get()) return
        ExecutorHelper.handler.removeCallbacks(connectionWatchdog)
        ExecutorHelper.handler.postDelayed(connectionWatchdog, delay)
    }

    private fun stopConnectionWatchdog() {
        ExecutorHelper.handler.removeCallbacks(connectionWatchdog)
    }

    private fun getNextWatchdogDelay(): Long {
        return when {
            hasNetworkTransport.get() && !isConnectionOk.get() -> TRANSITION_RECHECK_DELAY_MS
            !hasNetworkTransport.get() -> OFFLINE_RECHECK_DELAY_MS
            else -> STABLE_RECHECK_DELAY_MS
        }
    }

    internal fun startListeners() {
        listenersStarted.set(true)
        refreshConnectionStateAndSchedule()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                networkCallback?.let {
                    connectivityManager?.registerDefaultNetworkCallback(
                        it, ExecutorHelper.handler
                    )
                }
            } else {
                networkCallback?.let {
                    connectivityManager?.registerNetworkCallback(
                        NetworkRequest.Builder().build(),
                        it
                    )
                }
            }
        } catch (_: Throwable) {
        }
    }

    internal fun stopListeners() {
        listenersStarted.set(false)
        stopConnectionWatchdog()
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
        } catch (_: Throwable) {
        }
    }

    internal fun setState(newState: Boolean) {
        //cancel  SocketCheck if it planned
        if (newState != isConnectionOk.get()) {
            isConnectionOk.set(newState)
            LogCat.log("ConnectionStateListener detected new connection state - $newState")
            Connection.notifyConnectionChanged(newState)
        }
    }

    internal fun onScreenStateChanged() {
        ExecutorHelper.scope.launch {
            refreshConnectionStateAndSchedule()
        }
    }

    internal val isConnected: Boolean
        get() = isConnectionOk.get()
}
