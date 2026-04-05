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

package dev.skomlach.biometric.compat.impl.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import dev.skomlach.biometric.compat.R
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.BroadcastTools
import dev.skomlach.common.misc.BroadcastTools.registerGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.unregisterGlobalBroadcastIntent
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.themes.SystemMonetDialogs
import dev.skomlach.common.translate.LocalizationHelper
import kotlinx.coroutines.Runnable


class UntrustedAccessibilityFragment : Fragment() {
    companion object {
        private const val TAG = "UntrustedAccessibilityFragment"
        private const val INTENT_KEY = "UntrustedAccessibilityFragment.intent_key"
        private const val INTENT_RESULT = "UntrustedAccessibilityFragment.result"

        fun askForTrust(
            activity: FragmentActivity,
            callback: (Boolean) -> Unit,
        ) {
            LogCat.log("UntrustedAccessibilityFragment.askForPermissions()")

            val tag = TAG
            val oldFragment = activity.supportFragmentManager.findFragmentByTag(tag)
            val fragment = UntrustedAccessibilityFragment()
            registerGlobalBroadcastIntent(AndroidContext.appContext, object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    AndroidContext.resumedActivityLiveData.observeForever(object :
                        Observer<Activity?> {
                        private val observer = this
                        private val action = Runnable {
                            AndroidContext.activity?.let {
                                AndroidContext.resumedActivityLiveData.removeObserver(observer)
                                callback.invoke(intent.getBooleanExtra(INTENT_RESULT, false))
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
                        unregisterGlobalBroadcastIntent(AndroidContext.appContext, this)
                    } catch (e: Throwable) {
                        LogCat.logException(e)
                    }
                }
            }, IntentFilter(INTENT_KEY))
            activity
                .supportFragmentManager.beginTransaction()
                .apply {
                    if (oldFragment != null)
                        this.remove(oldFragment)
                }
                .add(fragment, tag).commitAllowingStateLoss()

        }
    }

    private var alert: Dialog? = null
    override fun onAttach(context: Context) {
        super.onAttach(context)
        lifecycleScope.launchWhenResumed {
            if (alert == null)
                try {
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
                    val shortText = LocalizationHelper.getLocalizedString(
                        requireActivity(),
                        R.string.biometriccompat_use_devicecredentials
                    )
                    val longText = LocalizationHelper.getLocalizedString(
                        requireActivity(),
                        R.string.biometriccompat_untrusted_a11y,
                        shortText,
                        title
                    )
                    val str = SpannableStringBuilder(longText)
                    //Should not happen anymore
                    try {
                        str.setSpan(
                            StyleSpan(Typeface.BOLD),
                            shortText.length,
                            longText.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } catch (e: Throwable) {
                        LogCat.logException(e)
                    }

                    alert = SystemMonetDialogs.showAlertDialog(requireActivity(), title = title,
                        message = str, cancelable = false,
                        negativeText = getString(android.R.string.cancel),
                        onNegative =  {
                            closeFragment(false)
                        },
                        positiveText = getString( android.R.string.ok),

                        onPositive = {
                            closeFragment(true)
                        })
                } catch (e: Throwable) {
                    LogCat.logException(
                        e, "UntrustedAccessibilityFragment", e.message
                    )
                    closeFragment(false)
                }
        }

    }

    private fun closeFragment(ok: Boolean) {
        alert?.dismiss()
        alert = null
        val tag = TAG
        activity?.supportFragmentManager?.findFragmentByTag(tag) ?: return
        try {
            activity?.supportFragmentManager?.beginTransaction()
                ?.remove(this@UntrustedAccessibilityFragment)
                ?.commitNowAllowingStateLoss()
        } catch (e: Throwable) {
            LogCat.logException(e)
        } finally {
            BroadcastTools.sendGlobalBroadcastIntent(
                AndroidContext.appContext,
                Intent(INTENT_KEY).apply {
                    this.putExtra(INTENT_RESULT, ok)
                })
        }
    }
}