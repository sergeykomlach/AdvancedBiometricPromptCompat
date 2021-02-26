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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
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
    override fun onDetach() {
        super.onDetach()
        sendGlobalBroadcastIntent(appContext, Intent(INTENT_KEY))
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val permissions: List<String> = arguments?.getStringArrayList(LIST_KEY)?: listOf()
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
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)
            ?.commitNowAllowingStateLoss()
    }

}