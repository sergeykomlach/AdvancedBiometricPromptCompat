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

package com.example.myapplication.utils

import android.widget.Toast
import androidx.fragment.app.Fragment
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl

fun Fragment.startBiometric(biometricAuthRequest: BiometricAuthRequest) {
    if (!BiometricManagerCompat.hasEnrolled(biometricAuthRequest)) {
        val result = BiometricManagerCompat.openSettings(requireActivity(), biometricAuthRequest)
        Toast.makeText(
            activity,
            "No enrolled biometric for - ${biometricAuthRequest.api}/${biometricAuthRequest.type}\nTrying to open system settings - $result",
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    val start = System.currentTimeMillis()
    BiometricLoggerImpl.e("CheckBiometric.start() for $biometricAuthRequest")
    val biometricPromptCompat = BiometricPromptCompat.Builder(
        biometricAuthRequest,
        requireActivity()
    )
        .setTitle("Biometric for Fragment: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more")
        .setSubtitle("Biometric Subtitle: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more")
        .setDescription("Biometric Description: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more")
        .setNegativeButton(
            "Cancel: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more",
            null
        )
        .build()

    BiometricLoggerImpl.e(
        "CheckBiometric.isEnrollChanged -  ${
            BiometricManagerCompat.isBiometricEnrollChanged(
                biometricAuthRequest
            )
        }"
    )


    biometricPromptCompat.authenticate(object : BiometricPromptCompat.AuthenticationCallback() {
        override fun onSucceeded(confirmed: Set<BiometricType>) {
            BiometricLoggerImpl.e("CheckBiometric.onSucceeded() for $confirmed")
            Toast.makeText(activity, "Succeeded - $confirmed", Toast.LENGTH_SHORT).show()
        }

        override fun onCanceled() {
            BiometricLoggerImpl.e("CheckBiometric.onCanceled()")
            Toast.makeText(activity, "Canceled", Toast.LENGTH_SHORT).show()
        }

        override fun onFailed(reason: AuthenticationFailureReason?) {
            BiometricLoggerImpl.e("CheckBiometric.onFailed() - $reason")
            Toast.makeText(activity, "Failure: $reason", Toast.LENGTH_SHORT).show()
        }

        override fun onUIOpened() {
            BiometricLoggerImpl.e("CheckBiometric.onUIOpened()")
            Toast.makeText(activity, "onUIOpened", Toast.LENGTH_SHORT).show()
        }

        override fun onUIClosed() {
            BiometricLoggerImpl.e("CheckBiometric.onUIClosed()")
            Toast.makeText(activity, "onUIClosed", Toast.LENGTH_SHORT).show()
        }
    })
    Toast.makeText(
        activity,
        "Start biometric ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
        Toast.LENGTH_SHORT
    ).show()
}