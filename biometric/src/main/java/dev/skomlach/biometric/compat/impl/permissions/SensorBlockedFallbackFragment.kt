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
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.R
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.permissionui.OwnedResultFragment
import dev.skomlach.common.themes.SystemMonetDialogs
import dev.skomlach.common.translate.LocalizationHelper


class SensorBlockedFallbackFragment : OwnedResultFragment<Unit>() {
    companion object {


        //Just a references to system resources
//   [`sensor_privacy_start_use_camera_notification_content_title`->`Unblock device camera`]
//   [`sensor_privacy_start_use_dialog_turn_on_button`->`Unblock`]
//   [`sensor_privacy_start_use_mic_notification_content_title`->`Unblock device microphone`]
//   [`face_sensor_privacy_enabled`->`To use Face Unlock, turn on Camera access in Settings > Privacy`]

        private const val TAG = "SensorBlockedFallbackFragment"
        private const val TITLE = "title"
        private const val MESSAGE = "message"
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

            val fragment = SensorBlockedFallbackFragment().apply {
                arguments = Bundle().apply {
                    putString(TITLE, title)
                    putString(MESSAGE, msg)
                }
            }
            launchOwnedResult(activity, fragment, TAG) {
                if (it != null) callback()
            }
        }
    }

    private var alert: Dialog? = null
    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Settings can change sensor access even when it returns RESULT_CANCELED.
        completeRequest(Unit)
    }

    override fun onDestroy() {
        alert?.dismiss()
        alert = null
        super.onDestroy()
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun intentCanBeResolved(intent: Intent): Boolean {
        val pm = context?.packageManager
        val pkgAppsList = pm?.queryIntentActivities(intent, 0) ?: emptyList()
        return pkgAppsList.isNotEmpty()
    }

    override fun launchRequest() {
        alert = SystemMonetDialogs.showAlertDialog(
            requireActivity(),
            title = arguments?.getString(TITLE),
            message = arguments?.getString(MESSAGE),
            negativeText = getString(android.R.string.cancel),
            onNegative = { completeRequest(Unit) },
            onCancel = { completeRequest(Unit) },
            positiveText = LocalizationHelper.getLocalizedString(
                appContext,
                R.string.biometriccompat_sensor_privacy_start_use_dialog_turn_on_button
            ),
            onPositive = {
                try {
                    val privacySettings = Intent(Settings.ACTION_PRIVACY_SETTINGS)
                    startForResult.launch(
                        if (intentCanBeResolved(privacySettings)) privacySettings else Intent(Settings.ACTION_SETTINGS)
                    )
                } catch (e: RuntimeException) {
                    LogCat.logException(e, TAG)
                    completeRequest(Unit)
                }
            }
        )
    }

}
