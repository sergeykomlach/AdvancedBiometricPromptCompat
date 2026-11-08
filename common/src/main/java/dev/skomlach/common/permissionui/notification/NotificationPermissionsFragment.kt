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

package dev.skomlach.common.permissionui.notification

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import dev.skomlach.common.R
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.permissionui.OwnedResultFragment
import dev.skomlach.common.permissionui.resolveApplicationTitle
import dev.skomlach.common.themes.SystemMonetDialogs
import dev.skomlach.common.translate.LocalizationHelper
import kotlinx.coroutines.Runnable
import java.lang.reflect.Field

class NotificationPermissionsFragment : OwnedResultFragment<Unit>() {
    companion object {
        private const val TAG = "NotificationPermissionsFragment"
        private const val PERMISSION_KEY = "permissions_type"
        private const val CHANNEL_ID = "channelId"
        private val semPlatformIntField: Field? by lazy {
            runCatching {
                VERSION::class.java.getDeclaredField("SEM_PLATFORM_INT").apply {
                    isAccessible = true
                }
            }.getOrNull()
        }

        //Also channels permission can't be toggled properly
        //https://www.reddit.com/r/Android/comments/1aezehk/one_ui_61_disables_one_of_androids_best/
        //https://www.androidpolice.com/samsung-disables-notification-channels-on-all-one-ui-61-devices/
        fun getOneUiVersion(): String {
            try {
                if (!isSemAvailable(AndroidContext.appContext)) {
                    return "" // was "1.0" originally but probably just a dummy value for one UI devices
                }
                val version: Int = (semPlatformIntField?.getInt(null) ?: return "") - 90000
                if (version < 0) {
                    // not one ui (could be previous Samsung OS)
                    return ""
                }
                return (version / 10000).toString() + "." + ((version % 10000) / 100)
            } catch (e: Throwable) {
                LogCat.logException(e)
            }
            return ""
        }

        private fun isSemAvailable(context: Context?): Boolean {
            return context != null &&
                    (context.packageManager.hasSystemFeature("com.samsung.feature.samsung_experience_mobile") ||
                            context.packageManager.hasSystemFeature("com.samsung.feature.samsung_experience_mobile_lite"))
        }


        fun askForPermissions(
            activity: FragmentActivity,
            type: PermissionRequestController.PermissionType,
            channelId: String?,
            callback: Runnable?,
        ) {
            launchOwned(activity, type, channelId) { callback?.run() }
        }

        internal fun launchOwned(
            activity: FragmentActivity,
            type: PermissionRequestController.PermissionType,
            channelId: String?,
            callback: (Boolean) -> Unit
        ): () -> Unit {
            val fragment = NotificationPermissionsFragment().apply {
                arguments = Bundle().apply {
                    putString(PERMISSION_KEY, type.name)
                    putString(CHANNEL_ID, channelId)
                }
            }
            return launchOwnedResult(activity, fragment, TAG) { callback(it != null) }
        }
    }

    private var alert: Dialog? = null

    private val startForResultForPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (!PermissionUtils.INSTANCE.hasSelfPermissions("android.permission.POST_NOTIFICATIONS")) {
                closeFragment()
            } else if (!PermissionUtils.INSTANCE.isAllowedNotificationsPermission) {
                generalNotification.invoke()
            } else {
                closeFragment()
            }

        }
    private val startForResult: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { closeFragment() }

    private val generalNotification = {
        val activity = requireActivity()
        val title = resolveApplicationTitle(this)
        alert = SystemMonetDialogs.showAlertDialog(
            requireActivity(), title = title, message = LocalizationHelper.getLocalizedString(
                activity,
                R.string.biometriccompat_request_perm,
                title,
                LocalizationHelper.getLocalizedString(
                    activity,
                    R.string.biometriccompat_allow_notifications_perm
                )
            ), cancelable = false, negativeText = getString(android.R.string.cancel),
            onNegative = {
                closeFragment()
            },
            positiveText = getString(android.R.string.ok),
            onPositive = {
                if (VERSION.SDK_INT >= 26) {
                    try {
                        if (safeStartActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context?.packageName),

                                )
                        )
                            return@showAlertDialog
                    } catch (e: Throwable) {
                        LogCat.logException(e)
                    }
                }

                try {
                    val launchIntent = Intent()
                    launchIntent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    launchIntent.putExtra("app_package", context?.packageName)
                    launchIntent.putExtra("app_uid", context?.applicationInfo?.uid)

                    if (intentCanBeResolved(launchIntent)) {
                        if (safeStartActivity(launchIntent))
                            return@showAlertDialog
                    }
                } catch (e: Throwable) {
                    LogCat.logException(e)
                }
                try {
                    val i = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                    i.addCategory(Intent.CATEGORY_DEFAULT)
                    if (intentCanBeResolved(i)) {
                        if (safeStartActivity(i))
                            return@showAlertDialog
                    }
                } catch (e: Throwable) {
                    LogCat.logException(e)
                }
                closeFragment()


            })
    }

    private val channelNotification = {

        val activity = requireActivity()

        val title = resolveApplicationTitle(this)
        alert = SystemMonetDialogs.showAlertDialog(
            requireActivity(),
            title = title,
            message = LocalizationHelper.getLocalizedString(
                activity,
                R.string.biometriccompat_request_perm,
                title,
                LocalizationHelper.getLocalizedString(
                    activity,
                    R.string.biometriccompat_allow_notifications_channel_perm
                )
            ),
            cancelable = false,
            negativeText = getString(android.R.string.cancel),
            onNegative = {
                closeFragment()
            },
            positiveText = getString(android.R.string.ok),
            onPositive = {
                if (VERSION.SDK_INT >= 26) {
                    try {
                        val channelId = arguments?.getString(CHANNEL_ID)
                        if (safeStartActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context?.packageName)
                                    if (shouldIncludeNotificationChannelId(getOneUiVersion()))
                                        putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                                }
                            )
                        )

                            return@showAlertDialog
                    } catch (e: Throwable) {
                        LogCat.logException(e)
                    }
                }
                try {
                    val launchIntent = Intent()
                    launchIntent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    launchIntent.putExtra("app_package", context?.packageName)
                    launchIntent.putExtra("app_uid", context?.applicationInfo?.uid)

                    if (intentCanBeResolved(launchIntent)) {
                        if (safeStartActivity(launchIntent))
                            return@showAlertDialog
                    }
                } catch (e: Throwable) {
                    LogCat.logException(e)
                }
                try {
                    val i = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                    i.addCategory(Intent.CATEGORY_DEFAULT)
                    if (intentCanBeResolved(i)) {
                        if (safeStartActivity(i))
                            return@showAlertDialog
                    }
                } catch (e: Throwable) {
                    LogCat.logException(e)
                }
                closeFragment()

            })


    }

    override fun launchRequest() {
        val type = PermissionRequestController.PermissionType.entries.firstOrNull {
            it.name == arguments?.getString(PERMISSION_KEY)
        }
        when (type) {
            PermissionRequestController.PermissionType.GENERAL_PERMISSION -> when {
                !PermissionUtils.INSTANCE.hasSelfPermissions("android.permission.POST_NOTIFICATIONS") ->
                    startForResultForPermissions.launch(arrayOf("android.permission.POST_NOTIFICATIONS"))
                !PermissionUtils.INSTANCE.isAllowedNotificationsPermission -> generalNotification()
                else -> closeFragment()
            }
            PermissionRequestController.PermissionType.CHANNEL_PERMISSION -> channelNotification()
            null -> closeFragment()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        alert?.dismiss()
        alert = null
    }

    private fun closeFragment() {
        completeRequest(Unit)
    }

    private fun safeStartActivity(
        intent: Intent
    ): Boolean {
        try {
            if (intentCanBeResolved(intent)) {
                startForResult.launch(intent)
                return true
            }
        } catch (throwable: Throwable) {
            LogCat.logException(throwable)
        }
        return false
    }

    private fun intentCanBeResolved(intent: Intent): Boolean {
        val pm = context?.packageManager
        val pkgAppsList = pm?.queryIntentActivities(intent, 0) ?: emptyList()
        return pkgAppsList.isNotEmpty()
    }

}

/**
 * Samsung disables reliable per-channel notification settings deep links starting
 * with One UI 6.1. Unknown versions keep the channel id so non-Samsung and
 * unreadable platform values preserve the standard Android behavior.
 */
internal fun shouldIncludeNotificationChannelId(oneUiVersion: String): Boolean {
    val parsedVersion = parseOneUiVersion(oneUiVersion) ?: return true
    val minimumBrokenVersion = 6 to 1
    return parsedVersion.first < minimumBrokenVersion.first ||
            (parsedVersion.first == minimumBrokenVersion.first &&
                    parsedVersion.second < minimumBrokenVersion.second)
}

private fun parseOneUiVersion(version: String): Pair<Int, Int>? {
    val parts = version.split(".")
    if (parts.size < 2) {
        return null
    }
    val major = parts[0].toIntOrNull() ?: return null
    val minor = parts[1].toIntOrNull() ?: return null
    return major to minor
}
