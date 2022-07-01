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
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.BiometricType
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
            if (purpose == CryptographyPurpose.ENCRYPT)
                managerInterface.getInitializedCipherForEncryption(
                    name,
                    isUserAuthRequired
                )
            else managerInterface.getInitializedCipherForDecryption(
                name,
                isUserAuthRequired
            )
        cache[cipher] = name
        return BiometricCryptoObject(signature = null, cipher = cipher, mac = null)
    }

    fun encryptData(plaintext: ByteArray, cipher: Cipher): ByteArray {
        val bytes = cipher.doFinal(plaintext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && managerInterface is CryptographyManagerInterfaceMarshmallowImpl) {
            cache[cipher]?.let {
                managerInterface.storeKeyPairInFallback(it, cipher.iv)
            }

        }
        return bytes
    }

    fun decryptData(ciphertext: ByteArray, cipher: Cipher): ByteArray {
        return cipher.doFinal(ciphertext)
    }

}