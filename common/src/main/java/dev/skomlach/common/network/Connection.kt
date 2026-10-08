/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Network
import android.os.PowerManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.BroadcastTools
import dev.skomlach.common.misc.ExecutorHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections

object Connection {

    private val connectionStateListener = ConnectionStateListener()
    private val internetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val transportDelegate = lazy { AndroidInternetProbe(connectionStateListener::currentNetwork) }
    private val transport by transportDelegate
    private val internetProbe = InternetProbe(NetworkReachability(connectionStateListener.networkState,
        connectionStateListener::refreshState, internetScope), EndpointProbe { endpoint, network, complete ->
        transport.start(endpoint, network, complete)
    }, InternetProbeConfiguration.endpoints)
    private val internetMonitor = InternetConnectivityMonitor(connectionStateListener.networkState,
        connectionStateListener::refreshState, { state -> internetProbe.check(state) }, internetScope)

    private val netlistLis: MutableList<NetworkListener> =
        Collections.synchronizedList(ArrayList<NetworkListener>())
    private var notifications: Job? = null
    private var policyUpdates: Job? = null
    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { updateProbeActivity() }
        override fun onStop(owner: LifecycleOwner) { updateProbeActivity() }
    }
    private val screenLockReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateProbeActivity()
            connectionStateListener.onScreenStateChanged()
            if (intent.action == Intent.ACTION_SCREEN_ON) internetMonitor.invalidate()
        }
    }

    init {
        if (LibraryNetworkPolicy.networkEnabled) internetMonitor.start() else internetMonitor.stop()
        policyUpdates = internetScope.launch {
            // A later successful lookup can come from any library HTTP entry point.
            LibraryNetworkPolicy.resolvedPolicy.filterNotNull().collect { policy ->
                if (internetScope.isActive && policy.networkEnabled) internetMonitor.start()
            }
        }
        initConnectionReceivers()
        ExecutorHelper.post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
            updateProbeActivity()
        }
        // One collector serializes legacy notifications. Client code never runs in an Android
        // callback or while the route registry is locked.
        notifications = internetScope.launch {
            internetMonitor.connectionChanges.collect { connected ->
                notifyConnectionChanged(connected)
            }
        }
    }

    private fun updateProbeActivity() {
        val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val interactive = (appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive != false
        internetMonitor.setForeground(visible && interactive)
        ensureMonitorStarted()
    }

    private fun ensureMonitorStarted() {
        // start() is idempotent. Never stop here: an unresolved lookup is retryable.
        if (internetScope.isActive && LibraryNetworkPolicy.networkEnabled) internetMonitor.start()
    }

    fun notifyConnectionChanged(lastKnownConnection: Boolean) {
        val listeners = synchronized(netlistLis) { netlistLis.toList() }
        LogCat.log("Connection new connection state - $lastKnownConnection")
        for (listener in listeners) {
            try {
                listener.networkChanged(lastKnownConnection)
            } catch (e: Exception) {
                LogCat.log("Connection listener failed:${e.javaClass.simpleName}")
            }
        }
    }

    private fun initConnectionReceivers() {
        //looking for the Screen ON/OFF
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_SCREEN_ON)
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        BroadcastTools.registerGlobalBroadcastIntent(appContext, screenLockReceiver, intentFilter)
        connectionStateListener.startListeners()
    }

    @Throws(Throwable::class)
    fun finalize() {
        internetScope.coroutineContext[Job]?.cancel()
        policyUpdates?.cancel()
        notifications?.cancel()
        ExecutorHelper.post { ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver) }
        BroadcastTools.unregisterGlobalBroadcastIntent(appContext, screenLockReceiver)
        internetMonitor.stop()
        connectionStateListener.stopListeners()
        if (transportDelegate.isInitialized()) transport.close()
    }

    internal val networkState: StateFlow<NetworkState>
        get() {
            ensureMonitorStarted()
            internetMonitor.read()
            return internetMonitor.states
        }

    internal val osNetworkState: StateFlow<NetworkState> get() = connectionStateListener.networkState

    internal fun refreshAndGetOsState(): NetworkState = connectionStateListener.refreshState()

    internal fun internetCheckConfigurationChanged() {
        if (internetProbe.configure(InternetProbeConfiguration.endpoints)) internetMonitor.invalidate()
        ensureMonitorStarted()
    }

    internal fun refreshAndGetState(): NetworkState {
        connectionStateListener.refreshState()
        ensureMonitorStarted()
        return internetMonitor.read()
    }

    internal fun stateForNetwork(network: Network): StateFlow<NetworkState> =
        connectionStateListener.stateFor(network)

    internal val isConnection: Boolean
        get() {
            ensureMonitorStarted()
            return internetMonitor.read().isConnected
        }

    internal fun refreshAndGetConnection(): Boolean {
        return refreshAndGetState().isConnected
    }

    internal fun hasNetworkTransport(): Boolean {
        return connectionStateListener.hasNetworkTransport()
    }

    fun addNetworkListener(listener: NetworkListener) {
        synchronized(netlistLis) {
            netlistLis.add(listener)
        }
        ensureMonitorStarted()
    }


    fun removeNetworkListener(listener: NetworkListener) {
        synchronized(netlistLis) {
            netlistLis.remove(listener)
        }
    }

    interface NetworkListener {
        /** Called only when confirmed Internet availability changes. */
        fun networkChanged(isConnected: Boolean)
    }
}
