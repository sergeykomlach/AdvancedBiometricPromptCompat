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
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.BroadcastTools
import java.util.Collections

object Connection {

    val connectionStateListener = ConnectionStateListener()

    private val netlistLis: MutableList<NetworkListener> =
        Collections.synchronizedList(ArrayList<NetworkListener>())
    private val screenLockReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            connectionStateListener.updateConnectionCheckQuery(0)
        }
    }

    init {
        initConnectionReceivers()
    }

    fun notifyConnectionChanged(lastKnownConnection: Boolean) {
        synchronized(netlistLis) {
            try {
                val netlistList = netlistLis.toMutableList()
                LogCat.logError("Connection new connection state - $lastKnownConnection")
                for (i in netlistList.indices) {
                    netlistList[i].networkChanged(lastKnownConnection)
                }
            } catch (_: ThreadDeath) {

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
        BroadcastTools.unregisterGlobalBroadcastIntent(appContext, screenLockReceiver)
        connectionStateListener.stopListeners()
    }

    val isConnection: Boolean
        get() = connectionStateListener.isConnected


    fun addNetworkListener(listener: NetworkListener) {
        synchronized(netlistLis) {
            netlistLis.add(listener)
        }
    }


    fun removeNetworkListener(listener: NetworkListener) {
        synchronized(netlistLis) {
            netlistLis.remove(listener)
        }
    }

    interface NetworkListener {
        fun networkChanged(isConnected: Boolean)
    }
}