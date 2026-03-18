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

package dev.skomlach.biometric.compat.utils.hardware

import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricProviderType
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.LegacyBiometric
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricModule
import dev.skomlach.biometric.compat.utils.BiometricLockoutFix
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e


class LegacyHardware(authRequest: BiometricAuthRequest) : AbstractHardware(authRequest) {
    override val isHardwareAvailable: Boolean
        get() {
            val biometricModules =
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) LegacyBiometric.availableBiometrics.map {
                    LegacyBiometric.getAvailableBiometricModule(it)
                }
                else listOf(
                    LegacyBiometric.getAvailableBiometricModule(
                        biometricAuthRequest.type
                    )
                )
            for (biometricModule in biometricModules) {
                val result = when (biometricAuthRequest.provider) {
                    BiometricProviderType.SOFTWARE if biometricModule is SoftwareBiometricModule -> {
                        biometricModule.isHardwarePresent
                    }

                    BiometricProviderType.HARDWARE if biometricModule !is SoftwareBiometricModule -> {
                        biometricModule?.isHardwarePresent == true
                    }

                    BiometricProviderType.COMBINED -> {
                        biometricModule?.isHardwarePresent == true
                    }

                    else -> false
                }

                if (result) return true.also {
                    e("LegacyHardware - isHardwareAvailable=$it; $biometricAuthRequest")
                }
            }

            return false.also {
                e("LegacyHardware - isHardwareAvailable=$it $biometricAuthRequest")
            }
        }
    override val isBiometricEnrolled: Boolean
        get() {
            val biometricModules =
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) LegacyBiometric.availableBiometrics.map {
                    LegacyBiometric.getAvailableBiometricModule(it)
                }
                else listOf(
                    LegacyBiometric.getAvailableBiometricModule(
                        biometricAuthRequest.type
                    )
                )
            for (biometricModule in biometricModules) {
                val result = when (biometricAuthRequest.provider) {
                    BiometricProviderType.SOFTWARE if biometricModule is SoftwareBiometricModule -> {
                        biometricModule.hasEnrolled
                    }

                    BiometricProviderType.HARDWARE if biometricModule !is SoftwareBiometricModule -> {
                        biometricModule?.hasEnrolled == true
                    }

                    BiometricProviderType.COMBINED -> {
                        biometricModule?.hasEnrolled == true
                    }

                    else -> false
                }

                if (result) return true.also {
                    e("LegacyHardware - isBiometricEnrolled=$it $biometricAuthRequest")
                }
            }

            return false.also {
                e("LegacyHardware - isBiometricEnrolled=$it $biometricAuthRequest")
            }
        }
    override val isLockedOut: Boolean
        get() {

            val biometricModules =
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) LegacyBiometric.availableBiometrics.map {
                    LegacyBiometric.getAvailableBiometricModule(it)
                }
                else listOf(
                    LegacyBiometric.getAvailableBiometricModule(
                        biometricAuthRequest.type
                    )
                )
            for (biometricModule in biometricModules) {
                val result = when (biometricAuthRequest.provider) {
                    BiometricProviderType.SOFTWARE if biometricModule is SoftwareBiometricModule -> {
                        biometricModule.isLockOut
                    }

                    BiometricProviderType.HARDWARE if biometricModule !is SoftwareBiometricModule -> {
                        biometricModule?.isLockOut == true
                    }

                    BiometricProviderType.COMBINED -> {
                        biometricModule?.isLockOut == true
                    }

                    else -> false
                }

                if (result) return true
            }

            return false

        }
    override val isBiometricEnrollChanged: Boolean
        get() {
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) return LegacyBiometric.isEnrollChanged()
            val biometricModule = LegacyBiometric.getAvailableBiometricModule(
                biometricAuthRequest.type
            )
            return when (biometricAuthRequest.provider) {
                BiometricProviderType.SOFTWARE if biometricModule is SoftwareBiometricModule -> {
                    biometricModule.isBiometricEnrollChanged
                }

                BiometricProviderType.HARDWARE if biometricModule !is SoftwareBiometricModule -> {
                    biometricModule?.isBiometricEnrollChanged == true
                }

                BiometricProviderType.COMBINED -> {
                    biometricModule?.isBiometricEnrollChanged == true
                }

                else -> false
            }
        }

    override fun updateBiometricEnrollChanged() {
        if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
            LegacyBiometric.updateBiometricEnrollChanged()
            return
        }
        val biometricModule = LegacyBiometric.getAvailableBiometricModule(
            biometricAuthRequest.type
        )
        (biometricModule as? AbstractBiometricModule)?.updateBiometricEnrollChanged()
    }

    override fun lockout() {
        if (!isLockedOut) {
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                for (type in BiometricType.entries) {
                    if (type == BiometricType.BIOMETRIC_ANY)
                        continue
                    BiometricLockoutFix.lockout(type)
                }
            } else
                BiometricLockoutFix.lockout(biometricAuthRequest.type)
        }
    }
}