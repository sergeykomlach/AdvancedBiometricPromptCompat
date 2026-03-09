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

import android.annotation.SuppressLint
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.launch


class InitiateSystemBiometricEnrollFragment : Fragment() {
    companion object {
        private const val INTENT_KEY = "InitiateSystemBiometricEnrollFragment.intent_key"

        fun showFragment(
            biometricAuthRequest: BiometricAuthRequest,
            callback: () -> Unit?
        ) {
            LogCat.log("InitiateSystemBiometricEnrollFragment", "showFragment")

            val tag =
                "${InitiateSystemBiometricEnrollFragment.javaClass.name}"
            val activity = AndroidContext.activity
            if (activity is FragmentActivity) {
                if (activity.supportFragmentManager.findFragmentByTag(tag) != null)
                    return
                registerGlobalBroadcastIntent(appContext, object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        callback.invoke()
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
            } else callback.invoke()
        }


    }

    private fun closeFragment() {
        LogCat.log("InitiateSystemBiometricEnrollFragment", "closeFragment")
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
            ExecutorHelper.postDelayed({
                closeFragment()
            }, 250)
        }

    override fun onDestroyView() {
        LogCat.log("InitiateSystemBiometricEnrollFragment", "onDestroyView")
        super.onDestroyView()
        startForResult.unregister()
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun intentCanBeResolved(intent: Intent): Boolean {
        val pm = context?.packageManager
        val pkgAppsList = pm?.queryIntentActivities(intent, 0) ?: emptyList()
        return pkgAppsList.isNotEmpty()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {

                try {
                    val biometricRequest: BiometricAuthRequest = BundleCompat.getParcelable(
                        arguments ?: return@repeatOnLifecycle,
                        "request",
                        BiometricAuthRequest::class.java
                    ) ?: return@repeatOnLifecycle
                    val intent =
                        LegacyBiometric.getSettingsIntent(biometricRequest.type) ?: Intent(
                            Settings.ACTION_SETTINGS
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

}