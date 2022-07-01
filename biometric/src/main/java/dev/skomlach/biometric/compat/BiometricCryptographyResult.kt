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

package dev.skomlach.biometric.compat

data class BiometricCryptographyResult(
    val biometricType: BiometricType,
    val data: ByteArray,
    val initializationVector: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BiometricCryptographyResult

        if (biometricType != other.biometricType) return false
        if (!data.contentEquals(other.data)) return false
        if (initializationVector != null) {
            if (other.initializationVector == null) return false
            if (!initializationVector.contentEquals(other.initializationVector)) return false
        } else if (other.initializationVector != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = biometricType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (initializationVector?.contentHashCode() ?: 0)
        return result
    }
}