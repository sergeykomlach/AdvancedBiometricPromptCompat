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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.BroadcastTools
import dev.skomlach.common.misc.BroadcastTools.registerGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.unregisterGlobalBroadcastIntent
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.translate.LocalizationHelper


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
        fun askForCameraUnblock(activity: FragmentActivity, callback: () -> Unit?) {
            showFragment(
                activity,
                LocalizationHelper.getLocalizedString(
                    appContext,
                    R.string.sensor_privacy_start_use_camera_notification_content_title
                ),
                LocalizationHelper.getLocalizedString(
                    appContext,
                    R.string.face_sensor_privacy_enabled
                ),
                callback
            )
        }

        fun askForMicUnblock(activity: FragmentActivity, callback: () -> Unit?) {
            showFragment(
                activity,
                LocalizationHelper.getLocalizedString(
                    appContext,
                    R.string.sensor_privacy_start_use_mic_notification_content_title
                ), null, callback
            )
        }

        private fun showFragment(
            activity: FragmentActivity,
            title: String?,
            msg: String?,
            callback: () -> Unit?
        ) {
            LogCat.log("SensorBlockedFragment", "showFragment $title $msg")
            if (title.isNullOrEmpty()) {
                callback.invoke()
                return
            }


            val tag =
                "${SensorBlockedFallbackFragment.javaClass.name}-${title.hashCode()}-${msg?.hashCode()}"


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

        }


    }

    private var alert: AlertDialog? = null
    private fun closeFragment() {
        alert?.dismiss()
        alert = null
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
            LogCat.log("SensorBlockedFragment", "startForResult")
            ExecutorHelper.postDelayed({
                closeFragment()
            }, 250)
        }

    override fun onDestroyView() {
        LogCat.log("SensorBlockedFragment", "onDestroyView")
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

        lifecycleScope.launchWhenResumed {
            if (alert == null)
                try {
                    alert = AlertDialog.Builder(requireActivity())
                        .setTitle(arguments?.getString(TITLE)).also { dialog ->
                            arguments?.getString(MESSAGE)?.let {
                                dialog.setMessage(it)
                            }
                        }
                        .setNegativeButton(
                            android.R.string.cancel
                        ) { _, _ -> closeFragment() }
                        .setPositiveButton(
                            LocalizationHelper.getLocalizedString(
                                appContext,
                                R.string.sensor_privacy_start_use_dialog_turn_on_button
                            )
                        ) { p0, _ ->
                            val intent =
                                if (intentCanBeResolved(Intent(Settings.ACTION_PRIVACY_SETTINGS)))
                                    Intent(Settings.ACTION_PRIVACY_SETTINGS)
                                else Intent(Settings.ACTION_SETTINGS)

                            startForResult.launch(intent)

                        }.show()
                } catch (e: Throwable) {
                    LogCat.logException(
                        e, "SensorBlockedFallbackFragment", e.message
                    )
                    closeFragment()
                }

        }
    }

}