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
import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.BroadcastTools
import dev.skomlach.common.misc.BroadcastTools.registerGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.unregisterGlobalBroadcastIntent
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.themes.SystemMonetDialogs
import dev.skomlach.common.translate.LocalizationHelper


class SensorBlockedFallbackFragment : Fragment() {
    companion object {


        //Just a references to system resources
//   [`sensor_privacy_start_use_camera_notification_content_title`->`Unblock device camera`]
//   [`sensor_privacy_start_use_dialog_turn_on_button`->`Unblock`]
//   [`sensor_privacy_start_use_mic_notification_content_title`->`Unblock device microphone`]
//   [`face_sensor_privacy_enabled`->`To use Face Unlock, turn on Camera access in Settings > Privacy`]

        private const val TAG = "SensorBlockedFallbackFragment"
        private const val TITLE = "title"
        private const val MESSAGE = "message"
        private const val INTENT_KEY = "SensorBlockedFallbackFragment.intent_key"
        fun askForCameraUnblock(activity: FragmentActivity, callback: () -> Unit?) {
            LogCat.log("SensorBlockedFragment", "askForCameraUnblock")
            showFragment(
                activity,
                LocalizationHelper.getLocalizedString(
                    appContext,
                    R.string.biometriccompat_sensor_privacy_start_use_camera_notification_content_title
                ),
                LocalizationHelper.getLocalizedString(
                    appContext,
                    R.string.biometriccompat_face_sensor_privacy_enabled
                ),
                callback
            )
        }

        fun askForMicUnblock(activity: FragmentActivity, callback: () -> Unit?) {
            LogCat.log("SensorBlockedFragment", "askForMicUnblock")
            showFragment(
                activity,
                LocalizationHelper.getLocalizedString(
                    appContext,
                    R.string.biometriccompat_sensor_privacy_start_use_mic_notification_content_title
                ), null, callback
            )
        }

        private fun showFragment(
            activity: FragmentActivity,
            title: String,
            msg: String?,
            callback: () -> Unit?
        ) {
            LogCat.log("SensorBlockedFragment", "showFragment $title $msg")

            val tag = TAG

            if (activity.supportFragmentManager.findFragmentByTag(tag) != null)
                return
            registerGlobalBroadcastIntent(appContext, object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    AndroidContext.resumedActivityLiveData.observeForever(object :
                        Observer<Activity?> {
                        private val observer = this
                        private val action = Runnable {
                            AndroidContext.activity?.let {
                                AndroidContext.resumedActivityLiveData.removeObserver(observer)
                                callback.invoke()
                            }
                        }

                        override fun onChanged(value: Activity?) {
                            if (value != null) {
                                ExecutorHelper.removeCallbacks(action)
                                ExecutorHelper.postDelayed(action, 250)
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
                .add(SensorBlockedFallbackFragment().apply {
                    this.arguments = Bundle().apply {
                        putString(TITLE, title)
                        putString(MESSAGE, msg)
                    }
                }, tag)
                .commitAllowingStateLoss()

        }


    }

    private var alert: Dialog? = null
    private fun closeFragment() {
        alert?.dismiss()
        alert = null
        LogCat.log("SensorBlockedFragment", "closeFragment")
        val tag = TAG
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
            if (it.resultCode == Activity.RESULT_OK) {
                closeFragment()

            } else { //workaround
                val observer = object : Observer<Activity?> {
                    private var waitForResume = false
                    override fun onChanged(t: Activity?) {
                        if (t == null) {
                            waitForResume = true
                            return
                        }
                        if (waitForResume && t == activity) {
                            AndroidContext.resumedActivityLiveData.removeObserver(this)
                            closeFragment()
                        }


                    }
                }
                AndroidContext.resumedActivityLiveData.observeForever(observer)
            }
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
                    alert = SystemMonetDialogs.showAlertDialog(
                        requireActivity(),
                        title = arguments?.getString(TITLE),
                        message = arguments?.getString(MESSAGE),
                        negativeText = getString(android.R.string.cancel),
                        onNegative = { closeFragment() },
                        positiveText = LocalizationHelper.getLocalizedString(
                            appContext,
                            R.string.biometriccompat_sensor_privacy_start_use_dialog_turn_on_button
                        ),
                        onPositive = {
                            val intent =
                                if (intentCanBeResolved(Intent(Settings.ACTION_PRIVACY_SETTINGS)))
                                    Intent(Settings.ACTION_PRIVACY_SETTINGS)
                                else Intent(Settings.ACTION_SETTINGS)

                            startForResult.launch(intent)
                        })
                } catch (e: Throwable) {
                    LogCat.logException(
                        e, "SensorBlockedFallbackFragment", e.message
                    )
                    closeFragment()
                }

        }
    }

}