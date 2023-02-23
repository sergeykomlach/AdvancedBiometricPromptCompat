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
package dev.skomlach.biometric.compat.crypto

import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricCryptographyResult
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl

object CryptographyManager {
    fun encryptData(
        plaintext: ByteArray?,
        confirmed: Set<AuthenticationResult>
    ): BiometricCryptographyResult? {
        if (plaintext == null)
            return null
        for (result in confirmed) {
            try {
                val type = result.confirmed ?: continue
                val cipher = result.cryptoObject?.cipher ?: continue
                val bytes = cipher.doFinal(plaintext)
                return BiometricCryptographyResult(type, bytes, cipher.iv)
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
        return null
    }

    fun decryptData(
        ciphertext: ByteArray?,
        confirmed: Set<AuthenticationResult>
    ): BiometricCryptographyResult? {
        if (ciphertext == null)
            return null
        for (result in confirmed) {
            try {
                val type = result.confirmed ?: continue
                val cipher = result.cryptoObject?.cipher ?: continue
                return BiometricCryptographyResult(type, cipher.doFinal(ciphertext))
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
        return null
    }
}