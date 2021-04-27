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

package dev.skomlach.biometric.compat.utils.hardware

import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.BiometricAuthentication

@RestrictTo(RestrictTo.Scope.LIBRARY)
class LegacyHardware(authRequest: BiometricAuthRequest) : AbstractHardware(authRequest) {
    val availableBiometricsCount: Int
        get() {
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                var count = 0
                for (type in BiometricAuthentication.availableBiometrics) {
                    val biometricModule = BiometricAuthentication.getAvailableBiometricModule(type)
                    if (biometricModule != null && biometricModule.isHardwarePresent && biometricModule.hasEnrolled()) {
                        count++
                    }
                }
                return count
            }
            val biometricModule = BiometricAuthentication.getAvailableBiometricModule(
                biometricAuthRequest.type
            )
            return if (biometricModule != null) 1 else 0
        }
    override val isHardwareAvailable: Boolean
        get() {
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) return BiometricAuthentication.isHardwareDetected
            val biometricModule = BiometricAuthentication.getAvailableBiometricModule(
                biometricAuthRequest.type
            )
            return biometricModule != null && biometricModule.isHardwarePresent
        }
    override val isBiometricEnrolled: Boolean
        get() {
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) return BiometricAuthentication.hasEnrolled()
            val biometricModule = BiometricAuthentication.getAvailableBiometricModule(
                biometricAuthRequest.type
            )
            return biometricModule != null && biometricModule.hasEnrolled()
        }
    override val isLockedOut: Boolean
        get() {
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) return BiometricAuthentication.isLockOut
            val biometricModule = BiometricAuthentication.getAvailableBiometricModule(
                biometricAuthRequest.type
            )
            return biometricModule != null && biometricModule.isLockOut
        }
}