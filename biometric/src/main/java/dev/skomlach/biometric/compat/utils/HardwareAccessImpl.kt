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
import dev.skomlach.biometric.compat.BiometricAuthState
import dev.skomlach.biometric.compat.utils.hardware.BiometricPromptHardware
import dev.skomlach.biometric.compat.utils.hardware.HardwareInfo
import dev.skomlach.biometric.compat.utils.hardware.LegacyHardware


class HardwareAccessImpl private constructor(val biometricAuthRequest: BiometricAuthRequest) {
    companion object {
        private val cache = HashMap<BiometricAuthRequest, HardwareInfo?>()
        fun getInstance(api: BiometricAuthRequest): HardwareAccessImpl {
            return HardwareAccessImpl(api)
        }

        private fun createHardwareInfo(
            biometricAuthRequest: BiometricAuthRequest
        ): HardwareInfo {
            return when (biometricAuthRequest.api) {
                BiometricApi.LEGACY_API -> {
                    LegacyHardware(biometricAuthRequest) //Android 4+
                }

                BiometricApi.BIOMETRIC_API -> {
                    BiometricPromptHardware(biometricAuthRequest) //new BiometricPrompt API; very raw on Android 9, so hacks and workarounds used
                }

                else -> { //AUTO
                    when {
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
    }

    val isBiometricEnrollChanged: Boolean
        get() = hardwareInfo?.isBiometricEnrollChanged == true

    fun updateBiometricEnrollChanged() {
        hardwareInfo?.updateBiometricEnrollChanged()
    }

    private var hardwareInfo: HardwareInfo? = null
    val isNewBiometricApi: Boolean
        get() = hardwareInfo !is LegacyHardware
    val isHardwareAvailable: Boolean
        get() = hardwareInfo?.isHardwareAvailable == true
    val isBiometricEnrolled: Boolean
        get() = hardwareInfo?.isBiometricEnrolled == true
    val isLockedOut: Boolean
        get() = hardwareInfo?.isLockedOut == true
    val authState: BiometricAuthState
        get() = hardwareInfo?.getAuthState() ?: BiometricAuthState(
            hardwareDetected = false,
            enrolled = false,
            lockedOut = false,
            permanentlyLocked = false
        )

    init {
        hardwareInfo = synchronized(cache) {
            cache[biometricAuthRequest] ?: createHardwareInfo(biometricAuthRequest).also {
                cache[biometricAuthRequest] = it
            }
        }
    }

    fun lockout() {
        hardwareInfo?.lockout()
    }
}
