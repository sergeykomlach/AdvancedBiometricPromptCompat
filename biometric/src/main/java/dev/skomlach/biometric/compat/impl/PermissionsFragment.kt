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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import com.google.common.util.concurrent.ListenableFuture
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider
import dev.skomlach.common.misc.BroadcastTools.registerGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.sendGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.unregisterGlobalBroadcastIntent
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.permissions.PermissionUtils
import java.util.*
import kotlin.collections.ArrayList


class PermissionsFragment : Fragment() {
    companion object {
        private const val LIST_KEY = "permissions_list"
        private const val INTENT_KEY = "intent_key"
        fun askForPermissions(
            activity: FragmentActivity,
            permissions: List<String>,
            callback: Runnable?
        ) {
            e("PermissionsFragment.askForPermissions()")
            if (permissions.isNotEmpty() && !PermissionUtils.hasSelfPermissions(permissions)) {
                val fragment = PermissionsFragment()
                val bundle = Bundle()
                bundle.putStringArrayList(LIST_KEY, ArrayList(permissions))
                fragment.arguments = bundle
                registerGlobalBroadcastIntent(appContext, object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (callback != null) ExecutorHelper.post(callback)
                        unregisterGlobalBroadcastIntent(appContext, this)
                    }
                }, IntentFilter(INTENT_KEY))
                activity
                    .supportFragmentManager.beginTransaction()
                    .add(fragment, fragment.javaClass.name).commitAllowingStateLoss()
            } else {
                if (callback != null) ExecutorHelper.post(callback)
            }
        }
    }

    private enum class PermissionRequestState {
        NORMAL_REQUEST,
        RATIONAL_REQUEST,
        MANUAL_REQUEST,
        NONE
    }

    @Volatile
    private var permissionsRequestState = PermissionRequestState.NONE
    override fun onDetach() {
        super.onDetach()
        sendGlobalBroadcastIntent(appContext, Intent(INTENT_KEY))
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val permissions: List<String> = arguments?.getStringArrayList(LIST_KEY) ?: listOf()
        if (permissions.isNotEmpty() && !PermissionUtils.hasSelfPermissions(permissions)) {
            ExecutorHelper.postDelayed({
                requestPermissions(permissions)
            }, 250)
        } else {
            ExecutorHelper.postDelayed({
                try {
                    activity?.supportFragmentManager?.beginTransaction()?.remove(this@PermissionsFragment)
                        ?.commitNowAllowingStateLoss()
                } catch (e: Throwable){
                    e("PermissionsFragment", e.message, e)
                }
            }, 250)
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionsRequestState == PermissionRequestState.MANUAL_REQUEST) {
            ExecutorHelper.postDelayed({
                try {
                    activity?.supportFragmentManager?.beginTransaction()?.remove(this@PermissionsFragment)
                        ?.commitNowAllowingStateLoss()
                } catch (e: Throwable){
                    e("PermissionsFragment", e.message, e)
                }
            }, 250)

        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissionsRequestState != PermissionRequestState.NONE) {
            ExecutorHelper.postDelayed({
                try {
                    activity?.supportFragmentManager?.beginTransaction()
                        ?.remove(this@PermissionsFragment)
                        ?.commitNowAllowingStateLoss()
                } catch (e: Throwable) {
                    e("PermissionsFragment", e.message, e)
                }
            }, 250)
        }
    }


    private fun unusedAppRestrictionsDisabled() {
        permissionsRequestState = PermissionRequestState.MANUAL_REQUEST
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
            startActivity(intent)
        } else
            ExecutorHelper.postDelayed({
                try {
                    activity?.supportFragmentManager?.beginTransaction()
                        ?.remove(this@PermissionsFragment)
                        ?.commitNowAllowingStateLoss()
                } catch (e: Throwable) {
                    e("PermissionsFragment", e.message, e)
                }
            }, 250)
    }

    private fun onResult(appRestrictionsStatus: Int) {
        permissionsRequestState = PermissionRequestState.MANUAL_REQUEST
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
            startActivityForResult(intent, 5678)
        } catch (e: Throwable) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", requireActivity().packageName, null)
            intent.data = uri
            startActivity(intent)
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
            showPermissionDeniedDialog(permissions, 1001)
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
                permissionsRequestState = PermissionRequestState.NORMAL_REQUEST
                //ask permission
                requestPermissions(
                    permissions.toTypedArray(),
                    1001
                )
            }
        }

    }

    /**
     * We show this custom dialog to alert user denied camera permission
     */
    private fun showPermissionDeniedDialog(permissions: List<String>, permissionRequestCode: Int) {
        val text = extractDescriptionsForPermissions(permissions)
        val title = getString("grant_permissions_header_text")
        if (text.isNullOrEmpty() || title.isNullOrEmpty()) {
            ExecutorHelper.postDelayed({
                try {
                    activity?.supportFragmentManager?.beginTransaction()?.remove(this@PermissionsFragment)
                        ?.commitNowAllowingStateLoss()
                } catch (e: Throwable){
                    e("PermissionsFragment", e.message, e)
                }
            }, 250)
        }
        AlertDialog.Builder(requireActivity()).apply {
            setTitle(title)
            setCancelable(true)
            setMessage(text)
            setOnCancelListener {
                ExecutorHelper.postDelayed({
                    try {
                        activity?.supportFragmentManager?.beginTransaction()?.remove(this@PermissionsFragment)
                            ?.commitNowAllowingStateLoss()
                    } catch (e: Throwable){
                        e("PermissionsFragment", e.message, e)
                    }
                }, 250)
            }
            setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                permissionsRequestState = PermissionRequestState.RATIONAL_REQUEST
                requestPermissions(
                    permissions.toTypedArray(),
                    permissionRequestCode
                )
            }
        }.show()
    }

    /**
     * We show this custom dialog to alert user that please go to settings to enable camera permission
     */
    private fun showMandatoryPermissionsNeedDialog(permissions: List<String>) {
        val text = extractDescriptionsForPermissions(permissions)
        val button = getString("turn_on_magnification_settings_action")
            ?: getString("global_action_settings")
        val title = getString("grant_permissions_header_text")
        if (text.isNullOrEmpty() || title.isNullOrEmpty() || button.isNullOrEmpty()) {
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

        AlertDialog.Builder(requireActivity()).apply {
            setTitle(title)
            setCancelable(true)
            setMessage(text)
            setOnCancelListener {
                ExecutorHelper.postDelayed({
                    try {
                        activity?.supportFragmentManager?.beginTransaction()?.remove(this@PermissionsFragment)
                            ?.commitNowAllowingStateLoss()
                    } catch (e: Throwable){
                        e("PermissionsFragment", e.message, e)
                    }
                }, 250)
            }
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
            TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == ViewCompat.LAYOUT_DIRECTION_LTR
        if (permissionsList.isNotEmpty()) {
            val sb = StringBuilder()
            for ((_, str) in permissionsList.keys.withIndex()) {
                val permName = permissionsList[str]
                if (!permName.isNullOrEmpty()) {
                    if (isLeftToRight)
                        sb.append("- $permName\n")
                    else
                        sb.append("\n$permName -")
                }
            }
            // Ask for all permissions
            return sb.toString().trim()
        }
        return null
    }

    //grant_permissions_header_text
    //turn_on_magnification_settings_action
    private fun getString(name: String): String? {
        try {
            val fields = Class.forName("com.android.internal.R\$string").declaredFields
            for (field in fields) {
                if (field.name.equals(name)) {
                    val isAccessible = field.isAccessible
                    return try {
                        if (!isAccessible) field.isAccessible = true
                        val s = requireActivity().getString(field[null] as Int)
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