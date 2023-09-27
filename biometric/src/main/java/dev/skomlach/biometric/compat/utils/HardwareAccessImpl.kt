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

package dev.skomlach.biometric.compat.utils

import androidx.core.os.BuildCompat
import dev.skomlach.biometric.compat.BiometricApi
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.utils.hardware.BiometricPromptHardware
import dev.skomlach.biometric.compat.utils.hardware.HardwareInfo
import dev.skomlach.biometric.compat.utils.hardware.LegacyHardware


class HardwareAccessImpl private constructor(val biometricAuthRequest: BiometricAuthRequest) {
    companion object {

        fun getInstance(api: BiometricAuthRequest): HardwareAccessImpl {
            return HardwareAccessImpl(api)
        }
    }

    val isBiometricEnrollChanged: Boolean
        get() = hardwareInfo?.isBiometricEnrollChanged ?: false

    fun updateBiometricEnrollChanged() {
        hardwareInfo?.updateBiometricEnrollChanged()
    }

    private var hardwareInfo: HardwareInfo? = null
    val isNewBiometricApi: Boolean
        get() = hardwareInfo !is LegacyHardware
    val isHardwareAvailable: Boolean
        get() = hardwareInfo?.isHardwareAvailable ?: false
    val isBiometricEnrolled: Boolean
        get() = hardwareInfo?.isBiometricEnrolled ?: false
    val isLockedOut: Boolean
        get() = hardwareInfo?.isLockedOut ?: false

    init {
        when (biometricAuthRequest.api) {
            BiometricApi.LEGACY_API -> {
                hardwareInfo = LegacyHardware(biometricAuthRequest) //Android 4+
            }

            BiometricApi.BIOMETRIC_API -> {
                hardwareInfo =
                    BiometricPromptHardware(biometricAuthRequest) //new BiometricPrompt API; very raw on Android 9, so hacks and workarounds used
            }

            else -> { //AUTO
                hardwareInfo = when {
                    BuildCompat.isAtLeastP() -> {
                        BiometricPromptHardware(biometricAuthRequest) //new BiometricPrompt API; very raw on Android 9, so hacks and workarounds used
                    }

                    else -> {
                        LegacyHardware(biometricAuthRequest) //Android 4+
                    }
                }
            }
        }
    }

    fun lockout() {
        hardwareInfo?.lockout()
    }
}