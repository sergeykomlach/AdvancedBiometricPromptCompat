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

data class BiometricCryptographyPurpose(val purpose: Int, val initVector: ByteArray? = null) {
    companion object {
        const val ENCRYPT = 1000
        const val DECRYPT = 1001
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BiometricCryptographyPurpose

        if (purpose != other.purpose) return false

        if (initVector != null) {
            if (other.initVector == null) return false
            if (!initVector.contentEquals(other.initVector)) return false
        } else if (other.initVector != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = purpose
        result = 31 * result + (initVector?.contentHashCode() ?: 0)
        return result
    }
}