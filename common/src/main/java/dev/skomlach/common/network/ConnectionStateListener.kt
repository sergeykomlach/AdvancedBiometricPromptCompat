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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ConnectionStateListener {

    private val isConnectionOk = AtomicBoolean(true)
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: NetworkCallback? = null


    init {
        connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        //Just an initial state; Will be checked properly later
        ExecutorHelper.scope.launch(Dispatchers.IO) {
            handleNetworkSignalChanged(
                isConnectionDetected()
            )
        }

        networkCallback = object : NetworkCallback() {
            override fun onUnavailable() {
                LogCat.logError("ConnectionStateListener onUnavailable")
                ExecutorHelper.scope.launch(Dispatchers.IO) {
                    handleNetworkSignalChanged(
                        false
                    )
                }
            }

            override fun onAvailable(network: Network) {
                LogCat.logError("ConnectionStateListener onAvailable")
                ExecutorHelper.scope.launch(Dispatchers.IO) {
                    handleNetworkSignalChanged(
                        true
                    )
                }
            }

            override fun onLost(network: Network) {
                LogCat.logError("ConnectionStateListener onLost")
                ExecutorHelper.scope.launch(Dispatchers.IO) {
                    handleNetworkSignalChanged(
                        false
                    )
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                LogCat.logError("ConnectionStateListener onCapabilitiesChanged")
                ExecutorHelper.scope.launch(Dispatchers.IO) {
                    handleNetworkSignalChanged(
                        isConnectionDetected()
                    )
                }
            }
        }

    }

    private fun handleNetworkSignalChanged(hasTransport: Boolean) {
        LogCat.log("ConnectionStateListener handleNetworkSignalChanged - $hasTransport")
        if (!hasTransport) {
            setState(false)
            return
        } else {
            val cm = connectivityManager
            val network =
                cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(network)

            setState(
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            )

        }
    }


    @Suppress("DEPRECATION")
    internal fun isConnectionDetected(): Boolean {
        return try {
            val cm = connectivityManager ?: return false
            val network =
                cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network)
                ?: return false

            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            false
        }
    }

    internal fun startListeners() {
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
        ExecutorHelper.scope.launch(Dispatchers.IO) {
            handleNetworkSignalChanged(
                isConnectionDetected()
            )
        }
    }

    internal val isConnected: Boolean
        get() = isConnectionOk.get()
}