/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
package dev.skomlach.biometric.compat.impl.dialogs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.BroadcastTools.registerGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.unregisterGlobalBroadcastIntent

class HomeWatcher(private val mListener: OnHomePressedListener) {
    private val mFilter: IntentFilter = IntentFilter()
    private val mReceiver = InnerReceiver()

    init {
        mFilter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        mFilter.addAction(Intent.ACTION_SCREEN_ON)
        mFilter.addAction(Intent.ACTION_SCREEN_OFF)
    }

    fun startWatch(): Runnable {
        return try {
            registerGlobalBroadcastIntent(AndroidContext.appContext, mReceiver, mFilter)
            Runnable {
                stopWatch()
            }
        } catch (e: Throwable) {
            Runnable { }
        }

    }

    private fun stopWatch() {
        try {
            unregisterGlobalBroadcastIntent(AndroidContext.appContext, mReceiver)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    interface OnHomePressedListener {
        fun onHomePressed()
        fun onRecentAppPressed()
        fun onPowerPressed()
    }

    private inner class InnerReceiver : BroadcastReceiver() {
        val SYSTEM_DIALOG_REASON_KEY = "reason"
        val SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS = "globalactions"
        val SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps"
        val SYSTEM_DIALOG_REASON_HOME_KEY = "homekey"
        val SYSTEM_DIALOG_REASON_DREAM = "dream"
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val action = intent.action
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS == action) {
                    val reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY)
                    if (SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS != reason) {
                        if (SYSTEM_DIALOG_REASON_HOME_KEY == reason) {
                            mListener.onHomePressed()
                        } else if (SYSTEM_DIALOG_REASON_RECENT_APPS == reason) {
                            mListener.onRecentAppPressed()
                        }

                    }
                } else if (Intent.ACTION_SCREEN_OFF == action || Intent.ACTION_SCREEN_ON == action) {
                    mListener.onPowerPressed()
                }
            } catch (e: Throwable) {
            }
        }
    }

}