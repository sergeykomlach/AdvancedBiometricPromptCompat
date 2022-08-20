/*
 *  Copyright (c) 2022 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package androidx.biometric

import android.annotation.SuppressLint
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl

object BiometricPromptApi28CancellationWorkaround {
    @SuppressLint("RestrictedApi")
    fun applyHook(biometricFragment: BiometricFragment) {
        try {
            val viewModel: BiometricViewModel =
                BiometricFragment::class.java.declaredMethods.first {
                    it.parameterTypes.isEmpty() && it.returnType == BiometricViewModel::class.java
                }.apply {
                    this.isAccessible = true
                }.invoke(biometricFragment) as BiometricViewModel

            val callback = viewModel.clientCallback
            val cancellationSignalProvider = viewModel.cancellationSignalProvider
            //listen for fingerprintCancellationSignal for API 28+
            cancellationSignalProvider.fingerprintCancellationSignal.setOnCancelListener {
                BiometricLoggerImpl.e("fingerprintCancellationSignal fired")
                callback.onAuthenticationError(
                    BiometricPrompt.ERROR_CANCELED,
                    biometricFragment.requireContext()
                        .getString(R.string.generic_error_user_canceled)
                )
            }
            //NOTE: biometricCancellationSignal.setOnCancelListener breaks the dialog dismissing
            BiometricLoggerImpl.e("register workarounds for CancellationSignal")
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }
}