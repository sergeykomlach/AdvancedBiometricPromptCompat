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
import dev.skomlach.biometric.compat.BiometricCryptographyPurpose

object BiometricCryptoObjectHelper {
    private val managerInterface: CryptographyManagerInterface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            CryptographyManagerInterfaceMarshmallowImpl()
        else
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                CryptographyManagerInterfaceKitkatImpl()
            else
                CryptographyManagerInterfaceLegacyImpl()

    fun getBiometricCryptoObject(
        name: String?,
        purpose: BiometricCryptographyPurpose?,
        isUserAuthRequired: Boolean = true
    ): BiometricCryptoObject? {
        if (purpose == null || name.isNullOrEmpty())
            return null
        val cipher =
            when (purpose.purpose) {
                BiometricCryptographyPurpose.ENCRYPT -> managerInterface.getInitializedCipherForEncryption(
                    name,
                    isUserAuthRequired
                )
                BiometricCryptographyPurpose.DECRYPT -> managerInterface.getInitializedCipherForDecryption(
                    name,
                    isUserAuthRequired,
                    purpose.initVector
                )
                else -> throw IllegalArgumentException("Cryptography purpose should be BiometricCryptographyPurpose.ENCRYPT or BiometricCryptographyPurpose.DECRYPT")
            }
        return BiometricCryptoObject(signature = null, cipher = cipher, mac = null)
    }

}