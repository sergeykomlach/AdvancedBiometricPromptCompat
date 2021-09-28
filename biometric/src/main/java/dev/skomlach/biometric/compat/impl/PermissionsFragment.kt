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
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.common.util.concurrent.ListenableFuture
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.misc.BroadcastTools.registerGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.sendGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.unregisterGlobalBroadcastIntent
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.permissions.PermissionUtils
import java.util.*

class PermissionsFragment : Fragment() {
    companion object {
        private const val LIST_KEY = "permissions_list"
        private const val INTENT_KEY = "intent_key"
        fun askForPermissions(
            activity: FragmentActivity,
            permissions: List<String>,
            callback: Runnable?
        ) {
            e("BiometricPromptCompat.askForPermissions()")
            if (permissions.isNotEmpty() && !PermissionUtils.INSTANCE.hasSelfPermissions(permissions)) {
                val fragment = PermissionsFragment()
                val bundle = Bundle()
                bundle.putStringArrayList(LIST_KEY, ArrayList(permissions))
                fragment.arguments = bundle
                registerGlobalBroadcastIntent(appContext, object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (callback != null) ExecutorHelper.INSTANCE.handler.post(callback)
                        unregisterGlobalBroadcastIntent(appContext, this)
                    }
                }, IntentFilter(INTENT_KEY))
                activity
                    .supportFragmentManager.beginTransaction()
                    .add(fragment, fragment.javaClass.name).commitAllowingStateLoss()
            } else {
                if (callback != null) ExecutorHelper.INSTANCE.handler.post(callback)
            }
        }
    }

    @Volatile
    private var permissionsAutoRevokeFlowStarted = false
    override fun onDetach() {
        super.onDetach()
        sendGlobalBroadcastIntent(appContext, Intent(INTENT_KEY))
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val permissions: List<String> = arguments?.getStringArrayList(LIST_KEY) ?: listOf()
        if (permissions.isNotEmpty() && !PermissionUtils.INSTANCE.hasSelfPermissions(permissions)) {
            requestPermissions(permissions.toTypedArray(), 100)
        } else {
            activity?.supportFragmentManager?.beginTransaction()?.remove(this)
                ?.commitNowAllowingStateLoss()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        val future: ListenableFuture<Int> =
            PackageManagerCompat.getUnusedAppRestrictionsStatus(requireActivity())
        future.addListener({ onResult(future.get()) },
            ContextCompat.getMainExecutor(requireActivity())
        )

    }

    override fun onResume() {
        super.onResume()
        if (permissionsAutoRevokeFlowStarted) {
            permissionsAutoRevokeFlowStarted = false
            activity?.supportFragmentManager?.beginTransaction()?.remove(this)
                ?.commitNowAllowingStateLoss()
        }
    }

    private fun onResult(appRestrictionsStatus: Int) {
        when (appRestrictionsStatus) {
            // If the user doesn't start your app for months, its permissions
            // will be revoked and/or it will be hibernated.
            // See the API_* constants for details.
            UnusedAppRestrictionsConstants.API_30_BACKPORT,
            UnusedAppRestrictionsConstants.API_30,
            UnusedAppRestrictionsConstants.API_31 ->  handleRestrictions()

            // Status could not be fetched. Check logs for details.
            UnusedAppRestrictionsConstants.ERROR,
                // Restrictions do not apply to your app on this device.
            UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE,
                // Restrictions have been disabled by the user for your app.
            UnusedAppRestrictionsConstants.DISABLED -> {
                activity?.supportFragmentManager?.beginTransaction()?.remove(this)
                    ?.commitNowAllowingStateLoss()
            }
        }
    }

    private fun handleRestrictions() {
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
        permissionsAutoRevokeFlowStarted = true
        startActivityForResult(intent, 5678)
    }
}