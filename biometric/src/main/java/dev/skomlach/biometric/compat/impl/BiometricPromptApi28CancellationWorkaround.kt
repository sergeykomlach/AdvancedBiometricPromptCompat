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

package dev.skomlach.biometric.compat.impl

import androidx.biometric.BiometricFragment
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricViewModel
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl

object BiometricPromptApi28CancellationWorkaround {
    fun applyHook(biometricFragment: BiometricFragment) {
        try {
            val viewModel: BiometricViewModel =
                BiometricFragment::class.java.declaredMethods.first {
                    it.parameterTypes.isEmpty() && it.returnType == BiometricViewModel::class.java
                }.apply {
                    this.isAccessible = true
                }.invoke(biometricFragment) as BiometricViewModel

            val callback =
                BiometricViewModel::class.java.declaredMethods.first {
                    it.parameterTypes.isEmpty() && it.returnType == BiometricPrompt.AuthenticationCallback::class.java
                }
                    .apply {
                        this.isAccessible = true
                    }
                    .invoke(viewModel) as BiometricPrompt.AuthenticationCallback



            (getFingerprintCancellationSignal(viewModel) as androidx.core.os.CancellationSignal?)?.setOnCancelListener {
                BiometricLoggerImpl.e("fingerprintCancellationSignal fired")
                callback.onAuthenticationError(
                    BiometricPrompt.ERROR_CANCELED,
                    biometricFragment.requireContext()
                        .getString(androidx.biometric.R.string.generic_error_user_canceled)
                )
            }
            (getBiometricCancellationSignal(viewModel) as android.os.CancellationSignal?)?.setOnCancelListener {
                BiometricLoggerImpl.e("biometricCancellationSignal fired")
                callback.onAuthenticationError(
                    BiometricPrompt.ERROR_CANCELED,
                    biometricFragment.requireContext()
                        .getString(androidx.biometric.R.string.generic_error_user_canceled)
                )
            }
            BiometricLoggerImpl.e("register workarounds for CancellationSignal")
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    private fun getFingerprintCancellationSignal(viewModel: BiometricViewModel): Any? {
        for (m in BiometricViewModel::class.java.declaredMethods) {
            try {
                val cancellationSignalProvider =
                    m
                        .apply {
                            this.isAccessible = true
                        }
                        .invoke(viewModel)

                return cancellationSignalProvider.javaClass.declaredMethods.first {
                    it.parameterTypes.isEmpty() && it.returnType == androidx.core.os.CancellationSignal::class.java
                }
                    .apply {
                        this.isAccessible = true
                    }
                    .invoke(cancellationSignalProvider)
            } catch (ignore: Throwable) {

            }
        }
        return null
    }

    private fun getBiometricCancellationSignal(viewModel: BiometricViewModel): Any? {
        for (m in BiometricViewModel::class.java.declaredMethods) {
            try {
                val cancellationSignalProvider =
                    m
                        .apply {
                            this.isAccessible = true
                        }
                        .invoke(viewModel)

                return cancellationSignalProvider.javaClass.declaredMethods.first {
                    it.parameterTypes.isEmpty() && it.returnType == android.os.CancellationSignal::class.java
                }
                    .apply {
                        this.isAccessible = true
                    }
                    .invoke(cancellationSignalProvider)
            } catch (ignore: Throwable) {

            }
        }
        return null

    }
}