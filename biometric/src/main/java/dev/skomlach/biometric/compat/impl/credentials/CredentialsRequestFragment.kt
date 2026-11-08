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
import android.content.Context.KEYGUARD_SERVICE
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.common.permissionui.OwnedResultFragment
import dev.skomlach.common.translate.LocalizationHelper

class CredentialsRequestFragment : OwnedResultFragment<Boolean>() {
    companion object {
        private const val TAG = "CredentialsRequestFragment"

        fun showFragment(
            activity: FragmentActivity,
            title: CharSequence?,
            description: CharSequence?,
            validator: (Boolean) -> Unit?
        ) {
            launchOwned(activity, title, description) { validator(it) }
        }

        internal fun launchOwned(
            activity: FragmentActivity,
            title: CharSequence?,
            description: CharSequence?,
            validator: (Boolean) -> Unit
        ): () -> Unit {
            val fragment = CredentialsRequestFragment().apply {
                arguments = Bundle().apply {
                    putCharSequence("title", title)
                    putCharSequence("description", description)
                }
            }
            return launchOwnedResult(activity, fragment, TAG) { result ->
                if (result == true) BiometricErrorLockoutPermanentFix.resetBiometricSensorPermanentlyLocked()
                validator(result == true)
            }
        }
    }

    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        completeRequest(it.resultCode == Activity.RESULT_OK)
    }

    @SuppressLint("PrivateResource")
    override fun launchRequest() {
        val context = requireContext()
        val title = arguments?.getCharSequence("title") ?: runCatching {
            resources.getString(androidx.biometric.R.string.use_screen_lock_label)
        }.getOrElse {
            LocalizationHelper.getLocalizedString(context, R.string.biometriccompat_use_screen_lock_label)
        }
        val description = arguments?.getCharSequence("description") ?: runCatching {
            resources.getString(androidx.biometric.R.string.screen_lock_prompt_message)
        }.getOrElse {
            LocalizationHelper.getLocalizedString(context, R.string.biometriccompat_screen_lock_prompt_message)
        }
        val manager = context.getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        val intent = manager?.createConfirmDeviceCredentialIntent(title, description)
        if (intent == null) completeRequest(false) else startForResult.launch(intent)
    }
}
