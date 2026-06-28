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
import dev.skomlach.biometric.compat.BiometricAuthState
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.LegacyBiometric
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModuleState
import dev.skomlach.biometric.compat.utils.BiometricLockoutFix
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

internal fun aggregateLegacyAuthState(
    moduleStates: Iterable<() -> BiometricModuleState>
): BiometricAuthState {
    var hardwareDetected = false
    var enrolled = false
    var lockedOut = false
    var allDetectedHardwarePermanentLocked = true

    for (stateProvider in moduleStates) {
        val state = stateProvider()
        if (!state.hardwarePresent) {
            continue
        }

        hardwareDetected = true
        enrolled = enrolled || state.enrolled
        lockedOut = lockedOut || state.lockedOut
        allDetectedHardwarePermanentLocked =
            allDetectedHardwarePermanentLocked && state.permanentlyLocked

        if (state.enrolled && !state.lockedOut && !state.permanentlyLocked) {
            return BiometricAuthState(
                hardwareDetected = true,
                enrolled = true,
                lockedOut = false,
                permanentlyLocked = false
            )
        }
    }

    return BiometricAuthState(
        hardwareDetected = hardwareDetected,
        enrolled = enrolled,
        lockedOut = lockedOut,
        permanentlyLocked = hardwareDetected && allDetectedHardwarePermanentLocked
    )
}

class LegacyHardware(authRequest: BiometricAuthRequest) : AbstractHardware(authRequest) {
    override val isHardwareAvailable: Boolean
        get() {
            for (biometricModule in biometricModules()) {
                val result = biometricModule.isHardwarePresent

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
            for (biometricModule in biometricModules()) {
                val result = biometricModule.hasEnrolled

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

            for (biometricModule in biometricModules()) {
                val result = biometricModule.isLockOut

                if (result) return true
            }

            return false

        }

    override fun getAuthState(): BiometricAuthState {
        return aggregateLegacyAuthState(
            biometricModules().map { module -> { module.getModuleState() } }
        ).also {
            e("LegacyHardware - getAuthState=$it $biometricAuthRequest")
        }
    }

    override val isBiometricEnrollChanged: Boolean
        get() {
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) return LegacyBiometric.isEnrollChanged()
            return LegacyBiometric.getAvailableBiometricModules(
                biometricAuthRequest.type,
                biometricAuthRequest.provider
            ).any { it.isBiometricEnrollChanged }
        }

    override fun updateBiometricEnrollChanged() {
        if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
            LegacyBiometric.updateBiometricEnrollChanged()
            return
        }
        LegacyBiometric.getAvailableBiometricModules(
            biometricAuthRequest.type,
            biometricAuthRequest.provider
        ).forEach {
            (it as? AbstractBiometricModule)?.updateBiometricEnrollChanged()
        }
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

    private fun biometricModules() =
        if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
            LegacyBiometric.availableBiometrics.flatMap {
                LegacyBiometric.getAvailableBiometricModules(it, biometricAuthRequest.provider)
            }
        } else {
            LegacyBiometric.getAvailableBiometricModules(
                biometricAuthRequest.type,
                biometricAuthRequest.provider
            )
        }
}
