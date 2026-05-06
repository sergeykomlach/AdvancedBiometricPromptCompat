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

package dev.skomlach.biometric.compat.crypto

import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.BiometricCryptographyPurpose
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import kotlinx.coroutines.sync.Mutex

object BiometricCryptoObjectHelper {
    private val mutex = Mutex()
    private val managerInterface: CryptographyManagerInterface =
        HybridCryptographyManagerInterface()

    fun deleteCrypto(name: String) {
        managerInterface.deleteKey(name)
    }

    fun getBiometricCryptoObject(
        name: String,
        purpose: BiometricCryptographyPurpose?,
        isUserAuthRequired: Boolean = true
    ): BiometricCryptoObject? {
        if (purpose == null)
            return null
        mutex.tryLock()
        try {
            prepareCryptoAccess(name, isUserAuthRequired)
            val cipher =
                when (purpose.purpose) {
                    BiometricCryptographyPurpose.ENCRYPT -> getCipherForEncryption(
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
        } catch (ex: IllegalArgumentException) {
            throw ex
        } catch (e: Throwable) {
            throw BiometricCryptoException(e)
        } finally {
            try {
                if (mutex.isLocked) mutex.unlock()
            } catch (e: Throwable) {
            }
        }

    }

    private fun getCipherForEncryption(
        name: String,
        isUserAuthRequired: Boolean
    ) = try {
        managerInterface.getInitializedCipherForEncryption(
            name,
            isUserAuthRequired
        )
    } catch (e: Throwable) {
        if (isUserAuthRequired && isNoKeystoreBiometricEnrollment(e)) {
            BiometricLoggerImpl.d(
                "BiometricCryptoObjectHelper: fallback to app-flow crypto for $name because AndroidKeyStore has no biometric enrollment usable for auth-per-use keys"
            )
            managerInterface.deleteKey(name)
            prepareAppFlowCrypto(name)
            managerInterface.getInitializedCipherForEncryption(
                name,
                false
            )
        } else {
            managerInterface.deleteKey(name)
            managerInterface.getInitializedCipherForEncryption(
                name,
                isUserAuthRequired
            )
        }
    }

    private fun prepareCryptoAccess(name: String, isUserAuthRequired: Boolean) {
        if (!isUserAuthRequired || AppFlowCryptoRegistry.getAccessType(name) == CryptoAccessType.APP_FLOW) {
            prepareAppFlowCrypto(name)
        } else {
            AppFlowCryptoFacade.registerKeyForBiometric(name)
        }
    }

    private fun prepareAppFlowCrypto(name: String) {
        AppFlowCryptoFacade.registerKeyForAppFlow(name)
        AppFlowCryptoFacade.unlockWithAppSecret(name, name.toCharArray().reversedArray())
    }

    private fun isNoKeystoreBiometricEnrollment(t: Throwable): Boolean {
        var current: Throwable? = t
        while (current != null) {
            val msg = current.message.orEmpty()
            if (msg.contains("At least one biometric", ignoreCase = true) &&
                msg.contains("must be enrolled to create keys", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

}