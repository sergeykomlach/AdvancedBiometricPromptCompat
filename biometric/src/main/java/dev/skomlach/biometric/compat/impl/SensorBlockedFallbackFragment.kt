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

package dev.skomlach.biometric.compat.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.utils.activityView.ActiveWindow
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import java.util.concurrent.atomic.AtomicBoolean


class SensorBlockedFallbackFragment : Fragment() {
    companion object {
        private val appContext = AndroidContext.appContext

        //Just a references to system resources
//   [`sensor_privacy_start_use_camera_notification_content_title`->`Unblock device camera`]
//   [`sensor_privacy_start_use_dialog_turn_on_button`->`Unblock`]
//   [`sensor_privacy_start_use_mic_notification_content_title`->`Unblock device microphone`]
//   [`face_sensor_privacy_enabled`->`To use Face Unlock, turn on Camera access in Settings > Privacy`]
        private val isFallbackFragmentShown = AtomicBoolean(false)
        private const val TITLE = "title"
        private const val MESSAGE = "message"
        fun isUnblockDialogShown(): Boolean {
            if (isFallbackFragmentShown.get())//fallback shown
                return true
            else {
                val activity = AndroidContext.activity
                if (activity is FragmentActivity) {
                    val windowDoNotLoseFocus = try {
                        ActiveWindow.getActiveWindow(
                            ActiveWindow.getActiveWindows(activity).toMutableList()
                        ).hasWindowFocus()
                    } catch (e: Throwable) {
                        false
                    }
                    return !windowDoNotLoseFocus
                }
            }

            return false
        }

        fun askForCameraUnblock() {
            showFragment(
                getString(
                    appContext,
                    "sensor_privacy_start_use_camera_notification_content_title"
                ),
                getString(appContext, "face_sensor_privacy_enabled")
            )
        }

        fun askForMicUnblock() {
            showFragment(
                getString(
                    appContext,
                    "sensor_privacy_start_use_mic_notification_content_title"
                ), null
            )
        }

        private fun showFragment(title: String?, msg: String?) {
            if (title.isNullOrEmpty())
                return
            ExecutorHelper.postDelayed(
                {
                    val tag =
                        "${SensorBlockedFallbackFragment.javaClass.name}-${title.hashCode()}-${msg?.hashCode()}"
                    val activity = AndroidContext.activity
                    if (activity is FragmentActivity) {
                        if (activity.supportFragmentManager.findFragmentByTag(tag) != null)
                            return@postDelayed
                        val windowDoNotLoseFocus = try {
                            ActiveWindow.getActiveWindow(
                                ActiveWindow.getActiveWindows(activity).toMutableList()
                            ).hasWindowFocus()
                        } catch (e: Throwable) {
                            false
                        }
                        if (windowDoNotLoseFocus) {
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
                },
                appContext.resources.getInteger(android.R.integer.config_longAnimTime)
                    .toLong() * 2
            )

        }

        private fun getString(context: Context, name: String): String? {
            try {
                val fields = Class.forName("com.android.internal.R\$string").declaredFields
                for (field in fields) {
                    if (field.name.equals(name)) {
                        val isAccessible = field.isAccessible
                        return try {
                            if (!isAccessible) field.isAccessible = true
                            val s = context.getString(field[null] as Int)
                            if (s.isEmpty())
                                throw RuntimeException("String is empty")
                            s
                        } finally {
                            if (!isAccessible) field.isAccessible = false
                        }
                    }
                }
            } catch (e: Throwable) {
                e(e)
            }
            return null
        }
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)

        try {
            val alert = AlertDialog.Builder(requireActivity())
                .setTitle(this.arguments?.getString(TITLE)).also { dialog ->
                    this.arguments?.getString(MESSAGE)?.let {
                        dialog.setMessage(it)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                    getString(
                        appContext,
                        "sensor_privacy_start_use_dialog_turn_on_button"
                    ) ?: getString(android.R.string.ok)
                ) { p0, _ ->
                    Utils.startActivity(
                        Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS),
                        context
                    )
                    p0.dismiss()
                }
                .setOnDismissListener {
                    try {
                        activity?.supportFragmentManager?.beginTransaction()
                            ?.remove(this@SensorBlockedFallbackFragment)
                            ?.commitNowAllowingStateLoss()
                    } catch (e: Throwable) {
                        e("SensorBlockedFragment", e.message, e)
                    } finally {
                        isFallbackFragmentShown.set(false)
                    }
                }
            alert.show()
            isFallbackFragmentShown.set(true)
        } catch (ignore: Throwable) {
            try {
                activity?.supportFragmentManager?.beginTransaction()
                    ?.remove(this@SensorBlockedFallbackFragment)
                    ?.commitNowAllowingStateLoss()
            } catch (e: Throwable) {
                e("SensorBlockedFragment", e.message, e)
            } finally {
                isFallbackFragmentShown.set(false)
            }
        }
    }

}