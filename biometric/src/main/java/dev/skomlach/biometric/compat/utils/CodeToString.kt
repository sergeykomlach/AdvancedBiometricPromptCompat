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

package dev.skomlach.biometric.compat.utils

import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.engine.BiometricCodes

@RestrictTo(RestrictTo.Scope.LIBRARY)
object CodeToString {

    fun getHelpCode(code: Int): String {
        return when (code) {
            BiometricCodes.BIOMETRIC_ACQUIRED_GOOD -> "BIOMETRIC_ACQUIRED_GOOD"
            BiometricCodes.BIOMETRIC_ACQUIRED_IMAGER_DIRTY -> "BIOMETRIC_ACQUIRED_IMAGER_DIRTY"
            BiometricCodes.BIOMETRIC_ACQUIRED_INSUFFICIENT -> "BIOMETRIC_ACQUIRED_INSUFFICIENT"
            BiometricCodes.BIOMETRIC_ACQUIRED_PARTIAL -> "BIOMETRIC_ACQUIRED_PARTIAL"
            BiometricCodes.BIOMETRIC_ACQUIRED_TOO_FAST -> "BIOMETRIC_ACQUIRED_TOO_FAST"
            BiometricCodes.BIOMETRIC_ACQUIRED_TOO_SLOW -> "BIOMETRIC_ACQUIRED_TOO_SLOW"
            else -> "Unknown BIOMETRIC_ACQUIRED - $code"
        }
    }


    fun getErrorCode(code: Int): String {
        return when (code) {
            BiometricCodes.BIOMETRIC_ERROR_CANCELED -> "BIOMETRIC_ERROR_CANCELED"
            BiometricCodes.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "BIOMETRIC_ERROR_HW_UNAVAILABLE"
            BiometricCodes.BIOMETRIC_ERROR_LOCKOUT -> "BIOMETRIC_ERROR_LOCKOUT"
            BiometricCodes.BIOMETRIC_ERROR_NO_SPACE -> "BIOMETRIC_ERROR_NO_SPACE"
            BiometricCodes.BIOMETRIC_ERROR_TIMEOUT -> "BIOMETRIC_ERROR_TIMEOUT"
            BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS -> "BIOMETRIC_ERROR_UNABLE_TO_PROCESS"
            BiometricCodes.BIOMETRIC_ERROR_HW_NOT_PRESENT -> "BIOMETRIC_ERROR_HW_NOT_PRESENT"
            BiometricCodes.BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> "BIOMETRIC_ERROR_LOCKOUT_PERMANENT"
            BiometricCodes.BIOMETRIC_ERROR_NO_BIOMETRICS -> "BIOMETRIC_ERROR_NO_BIOMETRICS"
            BiometricCodes.BIOMETRIC_ERROR_VENDOR -> "BIOMETRIC_ERROR_VENDOR"
            else -> "Unknown BIOMETRIC_ERROR - $code"
        }
    }
}