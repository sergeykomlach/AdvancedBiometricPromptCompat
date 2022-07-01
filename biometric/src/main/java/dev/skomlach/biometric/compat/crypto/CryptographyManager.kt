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

import android.os.Build
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.util.*
import javax.crypto.Cipher

object CryptographyManager {

    private val cache = WeakHashMap<Cipher, String>()
    private val managerInterface: CryptographyManagerInterface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            CryptographyManagerInterfaceMarshmallowImpl()
        else
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                CryptographyManagerInterfaceKitkatImpl()
            else
                CryptographyManagerInterfaceLegacyImpl()

    fun getBiometricCryptoObject(
        type: BiometricType,
        purpose: CryptographyPurpose?,
        isUserAuthRequired: Boolean = true
    ): BiometricCryptoObject? {
        return getBiometricCryptoObject(type.name, purpose, isUserAuthRequired)
    }

    fun getBiometricCryptoObject(
        name: String?,
        purpose: CryptographyPurpose?,
        isUserAuthRequired: Boolean = true
    ): BiometricCryptoObject? {
        if (purpose == null || name.isNullOrEmpty())
            return null
        val cipher =
            when (purpose.purpose) {
                CryptographyPurpose.ENCRYPT -> managerInterface.getInitializedCipherForEncryption(
                    name,
                    isUserAuthRequired
                )
                CryptographyPurpose.DECRYPT -> managerInterface.getInitializedCipherForDecryption(
                    name,
                    isUserAuthRequired,
                    purpose.initVector
                )
                else -> throw IllegalArgumentException("Cryptography purpose should be CryptographyPurpose.ENCRYPT or CryptographyPurpose.DECRYPT")
            }
        cache[cipher] = name
        return BiometricCryptoObject(signature = null, cipher = cipher, mac = null)
    }

    fun encryptData(
        plaintext: ByteArray,
        confirmed: Set<AuthenticationResult>
    ): CryptographyResult? {
        for (result in confirmed) {
            try {
                val type = result.confirmed ?: continue
                val cipher = result.cryptoObject?.cipher ?: continue
                val bytes = cipher.doFinal(plaintext)
                return CryptographyResult(type, bytes, cipher.iv)
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
        return null
    }

    fun decryptData(
        ciphertext: ByteArray,
        confirmed: Set<AuthenticationResult>
    ): CryptographyResult? {
        for (result in confirmed) {
            try {
                val type = result.confirmed ?: continue
                val cipher = result.cryptoObject?.cipher ?: continue
                return CryptographyResult(type, cipher.doFinal(ciphertext))
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
        return null
    }
}