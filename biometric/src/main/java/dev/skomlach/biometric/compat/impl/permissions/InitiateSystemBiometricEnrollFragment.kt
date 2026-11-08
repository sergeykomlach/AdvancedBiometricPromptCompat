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

package dev.skomlach.biometric.compat.impl.permissions

import android.content.Intent
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.engine.LegacyBiometric
import dev.skomlach.common.permissionui.OwnedResultFragment

class InitiateSystemBiometricEnrollFragment : OwnedResultFragment<Unit>() {
    companion object {
        private const val TAG = "InitiateSystemBiometricEnrollFragment"

        fun showFragment(activity: FragmentActivity, biometricAuthRequest: BiometricAuthRequest, callback: () -> Unit?) {
            launchOwned(activity, biometricAuthRequest) { callback() }
        }

        internal fun launchOwned(
            activity: FragmentActivity,
            request: BiometricAuthRequest,
            callback: (returnedFromSettings: Boolean) -> Unit
        ): () -> Unit {
            val fragment = InitiateSystemBiometricEnrollFragment().apply { this.request = request }
            return launchOwnedResult(activity, fragment, TAG) { callback(it != null) }
        }
    }

    private var request: BiometricAuthRequest? = null
    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Settings can return RESULT_CANCELED even when a template was added.
        completeRequest(Unit)
    }

    override fun launchRequest() {
        val intent = LegacyBiometric.getSettingsIntent(checkNotNull(request).type) ?: Intent(Settings.ACTION_SETTINGS)
        startForResult.launch(intent)
    }
}
