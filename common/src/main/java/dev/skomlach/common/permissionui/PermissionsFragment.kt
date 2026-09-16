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

package dev.skomlach.common.permissionui

import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import dev.skomlach.common.R
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.BroadcastTools
import dev.skomlach.common.misc.BroadcastTools.registerGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.unregisterGlobalBroadcastIntent
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.storage.SharedPreferenceProvider
import dev.skomlach.common.themes.SystemMonetDialogs
import dev.skomlach.common.translate.LocalizationHelper
import kotlinx.coroutines.Runnable

class PermissionsFragment : Fragment() {
    companion object {

        private const val TAG = "PermissionsFragment"
        private const val LIST_KEY = "permissions_list"
        private const val INTENT_KEY = "PermissionsFragment.intent_key"
        private const val PREFERENCES_NAME = "BiometricCompat_PermissionsFragment"
        private const val DENIED_KEY_PREFIX = "denied:"

        fun extractDescriptionsForPermissions(keys: List<String>): String? {
            val permissionsList = PermissionUtils.INSTANCE.getPermissions(keys)
            val isLeftToRight =
                TextUtilsCompat.getLayoutDirectionFromLocale(AndroidContext.systemLocale) == ViewCompat.LAYOUT_DIRECTION_LTR
            if (permissionsList.isNotEmpty()) {
                val sb = StringBuilder()
                for ((_, str) in permissionsList.keys.withIndex()) {
                    val permName = permissionsList[str]
                    if (!permName.isNullOrEmpty()) {
                        if (permissionsList.size > 1) {
                            if (isLeftToRight)
                                sb.append("- $permName\n")
                            else
                                sb.append("\n$permName -")
                        } else {
                            sb.append("$permName")
                        }
                    }
                }
                // Ask for all permissions
                return sb.toString().trim()
            }
            return null
        }

        fun askForPermissions(
            activity: FragmentActivity,
            permissions: List<String>,
            callback: Runnable?
        ) {
            LogCat.log("PermissionsFragment.askForPermissions()")
            if (permissions.isNotEmpty() && !PermissionUtils.INSTANCE.hasSelfPermissions(permissions)) {
                val tag = TAG
                if (activity.supportFragmentManager.findFragmentByTag(tag) != null)
                    return
                val fragment = PermissionsFragment()
                val bundle = Bundle()
                bundle.putStringArrayList(LIST_KEY, ArrayList(permissions))
                fragment.arguments = bundle
                registerGlobalBroadcastIntent(appContext, object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        AndroidContext.resumedActivityLiveData.observeForever(object :
                            Observer<Activity?> {
                            private val observer = this
                            private val action = Runnable {
                                AndroidContext.activity?.let {
                                    AndroidContext.resumedActivityLiveData.removeObserver(observer)
                                    callback?.run()
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
                    .add(fragment, tag).commitAllowingStateLoss()
            } else {
                if (callback != null) ExecutorHelper.post(callback)
            }
        }
    }

    private var alert: Dialog? = null
    private val startForResultForPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            LogCat.log("PermissionsFragment.ActivityResult()")
            val denied = grants.filterValues { !it }.keys
            markDenied(denied)
            // A completed runtime request returns control to the caller, including on denial.
            // Settings recovery belongs to a later explicit permission request.
            closeFragment()
        }
    private val startForResult: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // App settings normally returns RESULT_CANCELED, including after granting access.
            // The caller rechecks actual permissions; waiting for another pause/resume hangs it.
            closeFragment()
        }

    override fun onDestroyView() {
        super.onDestroyView()
        startForResult.unregister()
        startForResultForPermissions.unregister()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        lifecycleScope.launchWhenResumed {
            val permissions: List<String> = arguments?.getStringArrayList(LIST_KEY) ?: listOf()
            if (permissions.isNotEmpty() && !PermissionUtils.INSTANCE.hasSelfPermissions(
                    permissions
                )
            ) {

                try {
                    requestPermissions(permissions)
                } catch (e: Throwable) {
                    closeFragment()
                }
            } else {
                closeFragment()
            }
        }

    }

    private fun requestPermissions(permissions: List<String>) {
        val permissionsWithRationale = permissions.filterTo(HashSet()) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(),
                it
            )
        }
        when (permissionRequestDecision(
            permissions,
            permissionsWithRationale,
            previouslyDeniedPermissions(permissions)
        )) {
            PermissionRequestDecision.SHOW_RATIONALE -> {
                markDenied(permissionsWithRationale)
                showPermissionDeniedDialog(permissions)
            }

            PermissionRequestDecision.SHOW_MANDATORY_SETTINGS -> {
                showMandatoryPermissionsNeedDialog(permissions)
            }

            PermissionRequestDecision.REQUEST_RUNTIME -> {
                startForResultForPermissions.launch(permissions.toTypedArray())
            }
        }
    }

    private fun previouslyDeniedPermissions(permissions: Collection<String>): Set<String> {
        val preferences = SharedPreferenceProvider.getPreferences(PREFERENCES_NAME)
        return permissions.filterTo(HashSet()) { permission ->
            preferences.getBoolean(deniedPreferenceKey(permission), false)
        }
    }

    private fun markDenied(permissions: Collection<String>) {
        if (permissions.isEmpty()) {
            return
        }
        SharedPreferenceProvider.getPreferences(PREFERENCES_NAME).edit {
            permissions.forEach { permission ->
                putBoolean(deniedPreferenceKey(permission), true)
            }
        }
    }

    private fun deniedPreferenceKey(permission: String): String {
        return "$DENIED_KEY_PREFIX$permission"
    }

    /**
     * We show this custom dialog to alert user denied permission
     */
    private fun showPermissionDeniedDialog(permissions: List<String>) {
        val isLeftToRight =
            TextUtilsCompat.getLayoutDirectionFromLocale(AndroidContext.systemLocale) == ViewCompat.LAYOUT_DIRECTION_LTR
        val textStart =
            LocalizationHelper.getLocalizedString(
                appContext,
                R.string.biometriccompat_grant_permissions_header_text
            )
        val textEnd = extractDescriptionsForPermissions(permissions)
        val text = (if (isLeftToRight) "$textStart:" else ":$textStart") + "\n" + textEnd

        val title = resolveApplicationTitle(this)
        if (textEnd.isNullOrEmpty() || title.isEmpty()) {
            closeFragment()
        }
        alert = SystemMonetDialogs.showAlertDialog(
            requireActivity(), title = title, cancelable = false, message = text,
            onCancel = {
                closeFragment()
            },
            negativeText = (
                    getString(android.R.string.cancel)
                    ),
            onNegative = { closeFragment() },
            positiveText = getString(android.R.string.ok),
            onPositive = {
                startForResultForPermissions.launch(permissions.toTypedArray())
            }
        )
    }

    /**
     * We show this custom dialog to alert user that please go to settings to enable permission
     */
    private fun showMandatoryPermissionsNeedDialog(permissions: List<String>) {

        val button =
            LocalizationHelper.getLocalizedString(
                appContext,
                R.string.biometriccompat_global_action_settings
            )
        val textEnd = extractDescriptionsForPermissions(permissions)

        val title = resolveApplicationTitle(this)
        if (textEnd.isNullOrEmpty() || title.isEmpty()) {
            openAppPermissionSettings()
            return
        }

        val text = permissionSettingsMessage(requireContext(), textEnd)

        alert = SystemMonetDialogs.showAlertDialog(
            requireActivity(),
            title = title,
            cancelable = false,
            message = text,
            onCancel = {
                closeFragment()
            },
            negativeText = getString(
                android.R.string.cancel
            ),
            onNegative = { closeFragment() },
            positiveText = button,
            onPositive = { openAppPermissionSettings() })
    }


    private fun openAppPermissionSettings() {
        try {
            startForResult.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireActivity().packageName, null)
            })
        } catch (error: Throwable) {
            LogCat.logException(error)
            closeFragment()
        }
    }

    private fun closeFragment() {
        LogCat.logError("PermissionsFragment", "closeFragment")
        alert?.dismiss()
        alert = null
        val tag = TAG
        activity?.supportFragmentManager?.findFragmentByTag(tag) ?: return
        try {
            activity?.supportFragmentManager?.beginTransaction()
                ?.remove(this@PermissionsFragment)
                ?.commitNowAllowingStateLoss()
        } catch (e: Throwable) {
            LogCat.logException(e, "PermissionsFragment", e.message)
        } finally {
            BroadcastTools.sendGlobalBroadcastIntent(
                appContext, Intent(
                    INTENT_KEY
                )
            )
        }
    }
}
