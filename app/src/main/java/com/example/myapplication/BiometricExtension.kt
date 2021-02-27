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

package com.example.myapplication

import android.graphics.Color
import android.widget.Toast
import androidx.fragment.app.Fragment
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl

fun Fragment.startBiometric(biometricAuthRequest: BiometricAuthRequest) {
    if (!BiometricManagerCompat.hasEnrolled(biometricAuthRequest)) {
        BiometricManagerCompat.openSettings(requireActivity(), biometricAuthRequest)
        return
    }
    val start = System.currentTimeMillis()
    BiometricLoggerImpl.e("CheckBiometric.start() for $biometricAuthRequest")
    val biometricPromptCompat = BiometricPromptCompat.Builder(
        biometricAuthRequest,
        requireActivity()
    )
        .setTitle("Biometric for Fragment")
        .setNegativeButton("Cancel", null)
        .build()

    val context = activity?.applicationContext
    biometricPromptCompat.authenticate(object : BiometricPromptCompat.Result {
        override fun onSucceeded() {
            Toast.makeText(context, "Succeeded", Toast.LENGTH_SHORT).show()
        }

        override fun onCanceled() {
            Toast.makeText(context, "Canceled", Toast.LENGTH_SHORT).show()
        }

        override fun onFailed(reason: AuthenticationFailureReason?) {
            Toast.makeText(context, "Error: $reason", Toast.LENGTH_SHORT).show()
        }

        override fun onUIOpened() {
            Toast.makeText(context, "onUIOpened", Toast.LENGTH_SHORT).show()
        }

        override fun onUIClosed() {
            Toast.makeText(context, "onUIClosed", Toast.LENGTH_SHORT).show()
        }
    })
}