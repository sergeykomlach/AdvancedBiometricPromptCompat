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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private val listenersReady = CompletableDeferred<Boolean>()
    private val policyReads = Channel<Unit>(Channel.CONFLATED)
    private data class ForegroundCheck(val visible: Boolean, val generation: Long)
    private val foregroundChecks = MutableStateFlow(ForegroundCheck(false, 0))
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
        // ConnectivityManager and receiver registration can both cross Binder. Keep their
        // startup and teardown in the same process-owned worker, including early finalization.
        internetScope.launch(Dispatchers.IO) { observeAndroidNetworks() }
        internetScope.launch(Dispatchers.IO) {
            if (!listenersReady.await()) return@launch
            for (ignored in policyReads) {
                if (LibraryNetworkPolicy.networkEnabled || LibraryNetworkPolicy.resolvedPolicy.value != null) break
            }
        }
        internetScope.launch {
            if (!listenersReady.await()) return@launch
            // A later successful lookup can come from any library HTTP entry point.
            LibraryNetworkPolicy.resolvedPolicy.collect { policy ->
                if (policy?.networkEnabled == true && internetScope.isActive) internetMonitor.start()
                else if (policy != null) internetMonitor.stop()
            }
        }
        internetScope.launch(Dispatchers.IO) {
            foregroundChecks.collect { check ->
                val interactive = check.visible && runCatching {
                    (appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive != false
                }.getOrDefault(false)
                if (check == foregroundChecks.value) internetMonitor.setForeground(interactive)
            }
        }
        requestPolicyRead()
        ExecutorHelper.post {
            if (internetScope.isActive) {
                ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
                updateProbeActivity()
            }
        }
        // One collector serializes legacy notifications. Client code never runs in an Android
        // callback or while the route registry is locked.
        internetScope.launch {
            internetMonitor.connectionChanges.collect { connected ->
                notifyConnectionChanged(connected)
            }
        }
    }

    private fun updateProbeActivity() {
        val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        foregroundChecks.update { ForegroundCheck(visible, it.generation + 1) }
        requestPolicyRead()
    }

    private fun requestPolicyRead() {
        // The suspended worker retries unresolved PackageManager reads outside the UI thread.
        if (internetScope.isActive && LibraryNetworkPolicy.resolvedPolicy.value == null) {
            policyReads.trySend(Unit)
        }
    }

    internal suspend fun awaitNetworkListeners(timeoutMillis: Long): Boolean =
        withTimeoutOrNull(timeoutMillis) { listenersReady.await() } == true && internetScope.isActive

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

    private suspend fun observeAndroidNetworks() {
        var registeredContext: Context? = null
        try {
            val context = appContext
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            BroadcastTools.registerGlobalBroadcastIntent(context, screenLockReceiver, filter)
            registeredContext = context
            connectionStateListener.startListeners()
            listenersReady.complete(true)
            awaitCancellation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LogCat.log("Connection registration failed:${error.javaClass.simpleName}")
        } finally {
            listenersReady.complete(false)
            registeredContext?.let { BroadcastTools.unregisterGlobalBroadcastIntent(it, screenLockReceiver) }
            connectionStateListener.stopListeners()
        }
    }

    @Throws(Throwable::class)
    fun finalize() {
        internetScope.cancel()
        listenersReady.complete(false)
        policyReads.close()
        ExecutorHelper.post { ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver) }
        internetMonitor.stop()
        if (transportDelegate.isInitialized()) transport.close()
    }

    internal val networkState: StateFlow<NetworkState>
        get() {
            requestPolicyRead()
            internetMonitor.read()
            return internetMonitor.states
        }

    internal val osNetworkState: StateFlow<NetworkState> get() = connectionStateListener.networkState

    internal fun refreshAndGetOsState(): NetworkState = connectionStateListener.refreshState()

    internal fun internetCheckConfigurationChanged() {
        if (internetProbe.configure(InternetProbeConfiguration.endpoints)) internetMonitor.invalidate()
        requestPolicyRead()
    }

    internal fun refreshAndGetState(): NetworkState {
        connectionStateListener.refreshState()
        requestPolicyRead()
        return internetMonitor.read()
    }

    internal fun stateForNetwork(network: Network): StateFlow<NetworkState> =
        connectionStateListener.stateFor(network)

    internal val isConnection: Boolean
        get() {
            requestPolicyRead()
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
        requestPolicyRead()
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
