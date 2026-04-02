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

package dev.skomlach.biometric.compat.impl.permissions

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.engine.LegacyBiometric
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.BroadcastTools
import dev.skomlach.common.misc.BroadcastTools.registerGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.unregisterGlobalBroadcastIntent
import dev.skomlach.common.misc.ExecutorHelper


class InitiateSystemBiometricEnrollFragment : Fragment() {
    companion object {
        private const val INTENT_KEY = "InitiateSystemBiometricEnrollFragment.intent_key"

        private const val TAG = "InitiateSystemBiometricEnrollFragment"
        fun showFragment(
            activity: FragmentActivity,
            biometricAuthRequest: BiometricAuthRequest,
            callback: () -> Unit?
        ) {
            LogCat.log("InitiateSystemBiometricEnrollFragment", "showFragment")

            val tag = TAG


            if (activity.supportFragmentManager.findFragmentByTag(tag) != null)
                return
            registerGlobalBroadcastIntent(appContext, object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (AndroidContext.activity != null) {
                        ExecutorHelper.post {
                            callback.invoke()
                        }
                    } else AndroidContext.resumedActivityLiveData.observeForever(object :
                        Observer<Activity?> {
                        override fun onChanged(value: Activity?) {
                            if (value != null) {
                                AndroidContext.resumedActivityLiveData.removeObserver(this)
                                ExecutorHelper.post {
                                    callback.invoke()
                                }
                            }
                        }

                    })
                    try {
                        unregisterGlobalBroadcastIntent(appContext, this)
                    } catch (e: Throwable) {
                        LogCat.logException(e)
                    }
                }
            }, IntentFilter(INTENT_KEY))
            activity
                .supportFragmentManager.beginTransaction()
                .add(InitiateSystemBiometricEnrollFragment().apply {
                    arguments = Bundle().apply {
                        putParcelable("request", biometricAuthRequest)
                    }
                }, tag)
                .commitAllowingStateLoss()

        }


    }

    private fun closeFragment() {
        LogCat.log("InitiateSystemBiometricEnrollFragment", "closeFragment")
        val tag = TAG
        activity?.supportFragmentManager?.findFragmentByTag(tag) ?: return
        try {
            activity?.supportFragmentManager?.beginTransaction()
                ?.remove(this@InitiateSystemBiometricEnrollFragment)
                ?.commitNowAllowingStateLoss()
        } catch (e: Throwable) {
            e("InitiateSystemBiometricEnrollFragment", e.message, e)
        } finally {
            BroadcastTools.sendGlobalBroadcastIntent(
                appContext, Intent(
                    INTENT_KEY
                )
            )
        }
    }


    private val startForResult: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            LogCat.log("InitiateSystemBiometricEnrollFragment", "startForResult")
            closeFragment()
        }

    override fun onDestroyView() {
        LogCat.log("InitiateSystemBiometricEnrollFragment", "onDestroyView")
        super.onDestroyView()
        startForResult.unregister()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        LogCat.log("InitiateSystemBiometricEnrollFragment", "onAttach $arguments")
        lifecycleScope.launchWhenResumed {
            try {
                val biometricRequest: BiometricAuthRequest = BundleCompat.getParcelable(
                    arguments ?: return@launchWhenResumed,
                    "request",
                    BiometricAuthRequest::class.java
                ) ?: return@launchWhenResumed

                val intent =
                    LegacyBiometric.getSettingsIntent(biometricRequest.type) ?: Intent(
                        Settings.ACTION_SETTINGS
                    )
                LogCat.logError(
                    "InitiateSystemBiometricEnrollFragment",
                    "$biometricRequest -> $intent"
                )
                startForResult.launch(intent)
            } catch (e: Throwable) {
                LogCat.logException(
                    e, "InitiateSystemBiometricEnrollFragment", e.message
                )
                closeFragment()
            }
        }

    }

}