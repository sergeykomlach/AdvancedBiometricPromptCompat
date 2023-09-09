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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import com.google.common.util.concurrent.ListenableFuture
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.BroadcastTools
import dev.skomlach.common.misc.BroadcastTools.registerGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.unregisterGlobalBroadcastIntent
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.SystemStringsHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.storage.SharedPreferenceProvider

class PermissionsFragment : Fragment() {
    companion object {
        private val appContext = AndroidContext.appContext
        private const val LIST_KEY = "permissions_list"
        private const val INTENT_KEY = "intent_key"
        fun askForPermissions(
            activity: FragmentActivity,
            permissions: List<String>,
            callback: Runnable?
        ) {
            LogCat.log("PermissionsFragment.askForPermissions()")
            if (permissions.isNotEmpty() && !PermissionUtils.hasSelfPermissions(permissions)) {
                val tag = "${PermissionsFragment::class.java.name}-${
                    permissions.joinToString(",").hashCode()
                }"
                if (activity.supportFragmentManager.findFragmentByTag(tag) != null)
                    return
                val fragment = PermissionsFragment()
                val bundle = Bundle()
                bundle.putStringArrayList(LIST_KEY, ArrayList(permissions))
                fragment.arguments = bundle
                registerGlobalBroadcastIntent(appContext, object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (callback != null) ExecutorHelper.post(callback)
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

    private val startForResultForPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            ExecutorHelper.postDelayed({
                closeFragment()
            }, 250)
        }
    private val startForResult: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                ExecutorHelper.postDelayed({
                    closeFragment()
                }, 250)

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
                            ExecutorHelper.postDelayed({
                                closeFragment()
                            }, 250)
                        }


                    }
                }
                AndroidContext.resumedActivityLiveData.observeForever(observer)
            }
        }
    override fun onDestroyView() {
        super.onDestroyView()
        startForResult.unregister()
        startForResultForPermissions.unregister()
    }
    override fun onAttach(context: Context) {
        super.onAttach(context)
        val permissions: List<String> = arguments?.getStringArrayList(LIST_KEY) ?: listOf()
        if (permissions.isNotEmpty() && !PermissionUtils.hasSelfPermissions(permissions)) {
            ExecutorHelper.postDelayed({
                requestPermissions(permissions)
            }, 250)
        } else {
            closeFragment()
        }
    }

    private fun unusedAppRestrictionsDisabled() {
        val permissions: List<String> = arguments?.getStringArrayList(LIST_KEY) ?: listOf()
        if (!permissions.any {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    requireActivity(),
                    it
                )
            } && (SharedPreferenceProvider.getPreferences("BiometricCompat_PermissionsFragment")
                .getBoolean("denied", false))
        ) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", requireActivity().packageName, null)
            intent.data = uri
            startForResult.launch(intent)
        } else
            closeFragment()
    }

    private fun onResult(appRestrictionsStatus: Int) {
        when (appRestrictionsStatus) {
            // If the user doesn't start your app for months, its permissions
            // will be revoked and/or it will be hibernated.
            // See the API_* constants for details.
            UnusedAppRestrictionsConstants.API_30_BACKPORT,
            UnusedAppRestrictionsConstants.API_30,
            UnusedAppRestrictionsConstants.API_31 -> handleRestrictions()

            // Status could not be fetched. Check logs for details.
            UnusedAppRestrictionsConstants.ERROR,
                // Restrictions do not apply to your app on this device.
            UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE,
                // Restrictions have been disabled by the user for your app.
            UnusedAppRestrictionsConstants.DISABLED -> {
                unusedAppRestrictionsDisabled()
            }
        }
    }

    private fun handleRestrictions() {
        try {
            // If your app works primarily in the background, you can ask the user
            // to disable these restrictions. Check if you have already asked the
            // user to disable these restrictions. If not, you can show a message to
            // the user explaining why permission auto-reset and Hibernation should be
            // disabled. Tell them that they will now be redirected to a page where
            // they can disable these features.

            val intent = IntentCompat.createManageUnusedAppRestrictionsIntent(
                requireActivity(),
                requireActivity().packageName
            )

            // Must use startActivityForResult(), not startActivity(), even if
            // you don't use the result code returned in onActivityResult().

            startForResult.launch(intent)
        } catch (e: Throwable) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", requireActivity().packageName, null)
            intent.data = uri
            startForResult.launch(intent)
        }
    }

    private fun requestPermissions(permissions: List<String>) {
        if (permissions.any {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    requireActivity(),
                    it
                )
            }) {
            SharedPreferenceProvider.getPreferences("BiometricCompat_PermissionsFragment").edit()
                .putBoolean("denied", true).apply()
            showPermissionDeniedDialog(permissions)
            return
        } else {
            if (!permissions.any {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(),
                        it
                    )
                } && (SharedPreferenceProvider.getPreferences("BiometricCompat_PermissionsFragment")
                    .getBoolean("denied", false))
            ) {
                showMandatoryPermissionsNeedDialog(permissions)
                return
            } else {
                //ask permission
                startForResultForPermissions.launch(permissions.toTypedArray())
            }
        }
    }

    /**
     * We show this custom dialog to alert user denied permission
     */
    private fun showPermissionDeniedDialog(permissions: List<String>) {
        val isLeftToRight =
            TextUtilsCompat.getLayoutDirectionFromLocale(AndroidContext.systemLocale) == ViewCompat.LAYOUT_DIRECTION_LTR
        val textStart =
            SystemStringsHelper.getFromSystem(appContext, "grant_permissions_header_text")
        val textEnd = extractDescriptionsForPermissions(permissions)
        val text = (if (isLeftToRight) "$textStart:" else ":$textStart") + "\n" + textEnd

        val title = try {
            val appInfo =
                (if (Utils.isAtLeastT) requireActivity().packageManager.getApplicationInfo(
                    requireActivity().application.packageName,
                    PackageManager.ApplicationInfoFlags.of(0L)
                ) else requireActivity().packageManager.getApplicationInfo(
                    requireActivity().application.packageName,
                    0
                ))
            requireActivity().packageManager.getApplicationLabel(appInfo).ifEmpty {
                getString(appInfo.labelRes)
            }
        } catch (e: Throwable) {
            "Unknown"
        }
        if (textEnd.isNullOrEmpty() || textStart.isNullOrEmpty() || title.isNullOrEmpty()) {
            closeFragment()
        }
        AlertDialog.Builder(
            requireActivity()
        ).apply {
            setTitle(title)
            setCancelable(false)
            setMessage(text)
            setOnCancelListener {
                closeFragment()
            }
            setNegativeButton(
                android.R.string.cancel
            ) { dialog, which -> closeFragment() }
            setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                startForResultForPermissions.launch(permissions.toTypedArray())
            }
        }.show()
    }

    /**
     * We show this custom dialog to alert user that please go to settings to enable permission
     */
    private fun showMandatoryPermissionsNeedDialog(permissions: List<String>) {

        val button =
            SystemStringsHelper.getFromSystem(appContext, "turn_on_magnification_settings_action")
                ?: SystemStringsHelper.getFromSystem(appContext, "global_action_settings")
        val isLeftToRight =
            TextUtilsCompat.getLayoutDirectionFromLocale(AndroidContext.systemLocale) == ViewCompat.LAYOUT_DIRECTION_LTR
        val textStart =
            SystemStringsHelper.getFromSystem(appContext, "error_message_change_not_allowed")
        val textEnd = extractDescriptionsForPermissions(permissions)
        val text = (if (isLeftToRight) "$textStart:" else ":$textStart") + "\n" + textEnd

        val title = try {
            val appInfo =
                (if (Utils.isAtLeastT) requireActivity().packageManager.getApplicationInfo(
                    requireActivity().application.packageName,
                    PackageManager.ApplicationInfoFlags.of(0L)
                ) else requireActivity().packageManager.getApplicationInfo(
                    requireActivity().application.packageName,
                    0
                ))
            requireActivity().packageManager.getApplicationLabel(appInfo).ifEmpty {
                getString(appInfo.labelRes)
            }
        } catch (e: Throwable) {
            "Unknown"
        }
        if (textEnd.isNullOrEmpty() || textStart.isNullOrEmpty() || title.isNullOrEmpty() || button.isNullOrEmpty()) {
            try {
                val future: ListenableFuture<Int> =
                    PackageManagerCompat.getUnusedAppRestrictionsStatus(requireActivity())
                future.addListener(
                    { onResult(future.get()) },
                    ContextCompat.getMainExecutor(requireActivity())
                )
            } catch (e: Throwable) {
                unusedAppRestrictionsDisabled()
            }
            return
        }

        AlertDialog.Builder(requireActivity()).apply {
            setTitle(title)
            setCancelable(false)
            setMessage(text)
            setOnCancelListener {
                closeFragment()
            }
            setNegativeButton(
                android.R.string.cancel
            ) { dialog, which -> closeFragment() }
            setPositiveButton(button) { dialog, _ ->
                dialog.dismiss()
                try {
                    val future: ListenableFuture<Int> =
                        PackageManagerCompat.getUnusedAppRestrictionsStatus(requireActivity())
                    future.addListener(
                        { onResult(future.get()) },
                        ContextCompat.getMainExecutor(requireActivity())
                    )
                } catch (e: Throwable) {
                    unusedAppRestrictionsDisabled()
                }
            }
        }.show()
    }

    private fun extractDescriptionsForPermissions(keys: List<String>): String? {
        val permissionsList = PermissionUtils.getPermissions(keys)
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


    private fun closeFragment() {
        val permissions: List<String> = arguments?.getStringArrayList(LIST_KEY) ?: listOf()
        val tag = "${PermissionsFragment::class.java.name}-${
            permissions.joinToString(",").hashCode()
        }"
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