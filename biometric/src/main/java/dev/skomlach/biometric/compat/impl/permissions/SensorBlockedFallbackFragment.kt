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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.BroadcastTools
import dev.skomlach.common.misc.BroadcastTools.registerGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.unregisterGlobalBroadcastIntent
import dev.skomlach.common.misc.SystemStringsHelper
import kotlinx.coroutines.launch


class SensorBlockedFallbackFragment : Fragment() {
    companion object {


        //Just a references to system resources
//   [`sensor_privacy_start_use_camera_notification_content_title`->`Unblock device camera`]
//   [`sensor_privacy_start_use_dialog_turn_on_button`->`Unblock`]
//   [`sensor_privacy_start_use_mic_notification_content_title`->`Unblock device microphone`]
//   [`face_sensor_privacy_enabled`->`To use Face Unlock, turn on Camera access in Settings > Privacy`]

        private const val TITLE = "title"
        private const val MESSAGE = "message"
        private const val INTENT_KEY = "SensorBlockedFallbackFragment.intent_key"
        fun askForCameraUnblock(callback: () -> Unit?) {
            showFragment(
                SystemStringsHelper.getFromSystem(
                    appContext,
                    "sensor_privacy_start_use_camera_notification_content_title"
                ),
                SystemStringsHelper.getFromSystem(appContext, "face_sensor_privacy_enabled"),
                callback
            )
        }

        fun askForMicUnblock(callback: () -> Unit?) {
            showFragment(
                SystemStringsHelper.getFromSystem(
                    appContext,
                    "sensor_privacy_start_use_mic_notification_content_title"
                ), null, callback
            )
        }

        private fun showFragment(title: String?, msg: String?, callback: () -> Unit?) {
            LogCat.log("SensorBlockedFragment", "showFragment")
            if (title.isNullOrEmpty()) {
                callback.invoke()
                return
            }


            val tag =
                "${SensorBlockedFallbackFragment.javaClass.name}-${title.hashCode()}-${msg?.hashCode()}"
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
                    .add(SensorBlockedFallbackFragment().apply {
                        this.arguments = Bundle().apply {
                            putString(TITLE, title)
                            putString(MESSAGE, msg)
                        }
                    }, tag)
                    .commitAllowingStateLoss()
            } else callback.invoke()
        }


    }

    private fun closeFragment() {
        LogCat.log("SensorBlockedFragment", "closeFragment")
        activity?.supportFragmentManager?.findFragmentByTag(tag) ?: return
        try {
            activity?.supportFragmentManager?.beginTransaction()
                ?.remove(this@SensorBlockedFallbackFragment)
                ?.commitNowAllowingStateLoss()
        } catch (e: Throwable) {
            e("SensorBlockedFragment", e.message, e)
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
            closeFragment()
        }

    override fun onDestroyView() {
        LogCat.log("SensorBlockedFragment", "onDestroyView")
        super.onDestroyView()
        startForResult.unregister()
    }

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
                    val alert = AlertDialog.Builder(requireActivity())
                        .setTitle(arguments?.getString(TITLE)).also { dialog ->
                            arguments?.getString(MESSAGE)?.let {
                                dialog.setMessage(it)
                            }
                        }
                        .setNegativeButton(
                            android.R.string.cancel
                        ) { dialog, which -> closeFragment() }
                        .setPositiveButton(
                            SystemStringsHelper.getFromSystem(
                                appContext,
                                "sensor_privacy_start_use_dialog_turn_on_button"
                            ) ?: getString(android.R.string.ok)
                        ) { p0, _ ->
                            p0.dismiss()
                            val intent =
                                if (intentCanBeResolved(Intent(Settings.ACTION_PRIVACY_SETTINGS)))
                                    Intent(Settings.ACTION_PRIVACY_SETTINGS)
                                else Intent(Settings.ACTION_SETTINGS)

                            startForResult.launch(intent)

                        }

                    alert.show()
                } catch (ignore: Throwable) {
                    try {
                        activity?.supportFragmentManager?.beginTransaction()
                            ?.remove(this@SensorBlockedFallbackFragment)
                            ?.commitNowAllowingStateLoss()
                    } catch (e: Throwable) {
                        e("SensorBlockedFragment", e.message, e)
                    } finally {
                        BroadcastTools.sendGlobalBroadcastIntent(
                            appContext, Intent(
                                INTENT_KEY
                            )
                        )
                    }
                }
            }
        }
    }

}