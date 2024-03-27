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

package dev.skomlach.biometric.compat.impl.credentials

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.BroadcastTools
import dev.skomlach.common.misc.ExecutorHelper


class CredentialsRequestFragment : Fragment() {
    companion object {
        private val appContext = AndroidContext.appContext
        private const val INTENT_KEY = "CredentialsRequestFragment.intent_key"

        fun showFragment(
            activity: FragmentActivity,
            title: CharSequence?,
            description: CharSequence?,
            validator: (Boolean) -> Unit?,
        ) {
            val tag = "${CredentialsRequestFragment::class.java.name}"
            if (activity.supportFragmentManager.findFragmentByTag(tag) != null)
                return
            val fragment = CredentialsRequestFragment()
            fragment.arguments = Bundle().apply {
                this.putCharSequence("title", title)
                this.putCharSequence("description", description)
            }
            BroadcastTools.registerGlobalBroadcastIntent(
                appContext,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        ExecutorHelper.post {
                            val result = intent.getBooleanExtra("success", false)
                            LogCat.logError("CredentialsRequestFragment", result)
                            if (result) {
                                BiometricErrorLockoutPermanentFix.resetBiometricSensorPermanentlyLocked()
                            }
                            validator.invoke(result)
                        }
                        try {
                            BroadcastTools.unregisterGlobalBroadcastIntent(appContext, this)
                        } catch (e: Throwable) {
                            LogCat.logException(e)
                        }
                    }
                },
                IntentFilter(INTENT_KEY), ContextCompat.RECEIVER_NOT_EXPORTED
            )
            activity
                .supportFragmentManager.beginTransaction()
                .add(fragment, tag).commitAllowingStateLoss()
        }
    }

    private var success = false
    private val startForResult: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            LogCat.log("CredentialsRequestFragment", it)
            if (it.resultCode == Activity.RESULT_OK) {
                success = true
                ExecutorHelper.postDelayed({
                    closeFragment()
                }, 250)

            } else { //workaround
                success = false
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
        LogCat.log("CredentialsRequestFragment", "onDestroyView")
        super.onDestroyView()
        startForResult.unregister()
    }


    @SuppressLint("PrivateResource")
    override fun onAttach(context: Context) {
        LogCat.log("CredentialsRequestFragment", "onAttach")
        super.onAttach(context)
        try {
            //Create an intent to open device screen lock screen to authenticate
            //Pass the Screen Lock screen Title and Description
            val title = arguments?.getCharSequence("title")
                ?: resources.getString(androidx.biometric.R.string.use_screen_lock_label)
            val description = arguments?.getCharSequence("description")
                ?: resources.getString(androidx.biometric.R.string.screen_lock_prompt_message)
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                //Get the instance of KeyGuardManager
                val keyguardManager =
                    requireActivity().getSystemService(KEYGUARD_SERVICE) as KeyguardManager?
                keyguardManager?.createConfirmDeviceCredentialIntent(
                    title,
                    description
                )
            } else {
                null
            }
            startForResult.launch(intent)
        } catch (e: Throwable) {
            LogCat.logException(
                e, "CredentialsRequestFragment", e.message
            )
            closeFragment()
        }
    }

    private fun closeFragment() {
        activity?.supportFragmentManager?.findFragmentByTag(tag) ?: return
        LogCat.log("CredentialsRequestFragment", "closeFragment")
        try {
            activity?.supportFragmentManager?.beginTransaction()
                ?.remove(this@CredentialsRequestFragment)
                ?.commitNowAllowingStateLoss()
        } catch (e: Throwable) {
            LogCat.logException(
                e, "CredentialsRequestFragment", e.message
            )
        } finally {
            BroadcastTools.sendGlobalBroadcastIntent(
                appContext, Intent(
                    INTENT_KEY
                ).apply {
                    putExtra("success", success)
                }
            )
        }
    }
}