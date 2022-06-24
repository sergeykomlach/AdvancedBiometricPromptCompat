/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.ExecutorHelper
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object Connection {

    private val appContext = AndroidContext.appContext
    val connectionStateListener = ConnectionStateListener()

    private val connectivityManager: ConnectivityManager? =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    private val netlistLis: MutableList<NetworkListener> =
        Collections.synchronizedList(ArrayList<NetworkListener>())
    private val screenLockReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            connectionStateListener.updateConnectionCheckQuery(1)
        }
    }
    private val ACTION = "check_network"
    private var job: Runnable? = null
    private var lastActiveNetworkInfo: AtomicReference<Any?> = AtomicReference<Any?>(null)
    private var lastKnownConnection = false
    private val notifyNetworkChangedTask = {
        val netlistList = ArrayList(netlistLis)
        for (i in netlistList.indices) {
            netlistList[i].networkChanged(lastKnownConnection)
        }
    }
    private val checkConnection: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            checkConnection()
        }
    }

    init {
        initConnectionReceivers()
    }

    //schedule forced network check after last user interaction
    fun updateConnectionCheckDueToUserInteraction(delaySeconds: Long = 5) {
        connectionStateListener.updateConnectionCheckQuery(delaySeconds)
    }

    fun checkConnectionChanged() {
        LocalBroadcastManager.getInstance(appContext)
            .sendBroadcast(Intent(ACTION))
    }

    private fun checkConnection() {
        try {
            val activeNetworkInfo = if (Build.VERSION.SDK_INT >= 24)
                connectivityManager?.activeNetwork else
                connectivityManager?.activeNetworkInfo
            val isConnected = isConnection
            val isNetworkChanged =
                activeNetworkInfo != null && activeNetworkInfo != lastActiveNetworkInfo.get()
            if (isNetworkChanged || isConnected != lastKnownConnection) {
                lastActiveNetworkInfo.set(activeNetworkInfo)
                lastKnownConnection = isConnected
                notifyNetworkChange()
            }
        } catch (ignore: Throwable) {
        }
    }

    fun initConnectionReceivers() {
        //looking for the Screen ON/OFF
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_SCREEN_ON)
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        appContext.registerReceiver(screenLockReceiver, intentFilter)
        connectionStateListener.startListeners()
        LocalBroadcastManager.getInstance(appContext)
            .registerReceiver(checkConnection, IntentFilter(ACTION))
    }

    fun close() {
        appContext.unregisterReceiver(screenLockReceiver)
        connectionStateListener.stopListeners()
        LocalBroadcastManager.getInstance(appContext)
            .unregisterReceiver(checkConnection)
    }

    val isConnection: Boolean
        get() {
            connectionStateListener.updateConnectionCheckQuery(0)
            return connectionStateListener.isConnected
        }
    val isWiFi: Boolean
        get() {
            if (Build.VERSION.SDK_INT >= 24) {
                val activeNetwork = connectivityManager?.activeNetwork ?: return false
                return connectivityManager.getNetworkCapabilities(activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } else {
                connectivityManager?.activeNetworkInfo?.let {
                    return listOf(
                        ConnectivityManager.TYPE_ETHERNET,
                        ConnectivityManager.TYPE_WIFI,
                        ConnectivityManager.TYPE_WIMAX
                    ).contains(it.type)
                }
            }
            return false
        }


    fun addNetworkListener(listener: NetworkListener) {
        netlistLis.add(listener)
    }


    fun removeNetworkListener(listener: NetworkListener) {
        netlistLis.remove(listener)
    }

    private fun notifyNetworkChange() {
        job?.let {
            ExecutorHelper.removeCallbacks(it)
        }
        job = Runnable { notifyNetworkChangedTask.invoke() }
        job?.let {
            ExecutorHelper.postDelayed(it, TimeUnit.SECONDS.toMillis(1))
        }
    }

    interface NetworkListener {
        fun networkChanged(isConnected: Boolean)
    }
}