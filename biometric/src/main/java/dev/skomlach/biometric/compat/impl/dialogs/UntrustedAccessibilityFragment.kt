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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.skomlach.biometric.compat.R
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.BroadcastTools
import dev.skomlach.common.misc.BroadcastTools.registerGlobalBroadcastIntent
import dev.skomlach.common.misc.BroadcastTools.unregisterGlobalBroadcastIntent
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.translate.LocalizationHelper


class UntrustedAccessibilityFragment : Fragment() {
    companion object {
        private const val TAG = "NotificationPermissionsFragment"
        private const val INTENT_KEY = "NotificationPermissionsFragment.intent_key"
        private const val INTENT_RESULT = "NotificationPermissionsFragment.result"
        fun preloadTranslations() {
            ExecutorHelper.startOnBackground {
                LocalizationHelper.prefetch(
                    AndroidContext.appContext,
                    R.string.biometriccompat_use_devicecredentials,
                    R.string.biometriccompat_untrusted_a11y
                )
            }
        }

        fun askForTrust(
            activity: FragmentActivity,
            callback: (Boolean) -> Unit,
        ) {
            LogCat.log("NotificationPermissionsFragment.askForPermissions()")

            val tag = "${UntrustedAccessibilityFragment::class.java.name}"
            val oldFragment = activity.supportFragmentManager.findFragmentByTag(tag)
            val fragment = UntrustedAccessibilityFragment()
            registerGlobalBroadcastIntent(AndroidContext.appContext, object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    callback.invoke(intent.getBooleanExtra(INTENT_RESULT, false))
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

    override fun onAttach(context: Context) {
        super.onAttach(context)

        lifecycleScope.launchWhenResumed {
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

                val alert = AlertDialog.Builder(requireActivity())
                    .setTitle(title)
                    .setMessage(str)
                    .setOnDismissListener {
                        closeFragment(false)
                    }
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.cancel) { p0, _ ->
                        closeFragment(false)
                    }
                    .setPositiveButton(
                        android.R.string.ok
                    ) { p0, _ ->
                        closeFragment(true)
                    }

                alert.show()
            } catch (e: Throwable) {
                closeFragment(false)
            }
        }
    }

    private fun closeFragment(ok: Boolean) {
        val tag = "${UntrustedAccessibilityFragment::class.java.name}"
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