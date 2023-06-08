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

package dev.skomlach.common.misc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.skomlach.common.logging.LogCat.logError

object BroadcastTools {
    private const val androidIntentAction = "android."


    fun sendGlobalBroadcastIntent(context: Context, intent: Intent) {
        val action = intent.action
        if (!action.isNullOrEmpty() && !action.startsWith(androidIntentAction)) {
            logError("BroadcastTools: You tried to send custom global BroadcastIntent. Make sure that action `$action` contains package-specific name")
        }
        context.sendBroadcast(intent)
    }


    fun registerGlobalBroadcastIntent(
        context: Context,
        broadcastReceiver: BroadcastReceiver?,
        filter: IntentFilter
    ) {
        val actionsIterator = filter.actionsIterator()
        while (actionsIterator.hasNext()) {
            val action = actionsIterator.next()
            if (!action.isNullOrEmpty() && !action.startsWith(androidIntentAction)) {
                logError("BroadcastTools: You tried to register custom global BroadcastReceiver. Make sure that action `$action` contains package-specific name")
            }
        }
        ContextCompat.registerReceiver(context, broadcastReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }


    fun unregisterGlobalBroadcastIntent(context: Context, broadcastReceiver: BroadcastReceiver?) {
        context.unregisterReceiver(broadcastReceiver)
    }
}