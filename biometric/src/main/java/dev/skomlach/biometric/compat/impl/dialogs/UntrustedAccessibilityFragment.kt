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

import android.app.Dialog
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.R
import dev.skomlach.common.permissionui.OwnedResultFragment
import dev.skomlach.common.permissionui.resolveApplicationTitle
import dev.skomlach.common.themes.SystemMonetDialogs
import dev.skomlach.common.translate.LocalizationHelper


class UntrustedAccessibilityFragment : OwnedResultFragment<Boolean>() {
    companion object {
        private const val TAG = "UntrustedAccessibilityFragment"

        fun askForTrust(activity: FragmentActivity, callback: (Boolean) -> Unit) {
            launchOwned(activity, callback)
        }

        internal fun launchOwned(activity: FragmentActivity, callback: (Boolean) -> Unit): () -> Unit =
            launchOwnedResult(activity, UntrustedAccessibilityFragment(), TAG) { callback(it == true) }
    }

    private var alert: Dialog? = null

    override fun launchRequest() {
        val title = resolveApplicationTitle(this)
        val shortText = LocalizationHelper.getLocalizedString(requireActivity(), R.string.biometriccompat_use_devicecredentials)
        val longText = LocalizationHelper.getLocalizedString(
            requireActivity(), R.string.biometriccompat_untrusted_a11y, shortText, title
        )
        val message = SpannableStringBuilder(longText)
        if (shortText.length < longText.length) {
            message.setSpan(StyleSpan(Typeface.BOLD), shortText.length, longText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        alert = SystemMonetDialogs.showAlertDialog(
            requireActivity(), title = title, message = message, cancelable = false,
            negativeText = getString(android.R.string.cancel), onNegative = { completeRequest(false) },
            positiveText = getString(android.R.string.ok), onPositive = { completeRequest(true) }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        alert?.dismiss()
        alert = null
    }
}
