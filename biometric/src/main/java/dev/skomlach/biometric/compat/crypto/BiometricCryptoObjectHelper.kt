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
import dev.skomlach.biometric.compat.CryptoSecurityLevel
import java.util.concurrent.locks.ReentrantLock

object BiometricCryptoObjectHelper {
    private val lock = ReentrantLock()
    private val managerInterface = HybridCryptographyManagerInterface()
    private val biometricDelegate = managerInterface.biometricDelegate

    fun deleteCrypto(name: String) {
        managerInterface.deleteKey(name)
    }

    fun getBiometricCryptoObject(
        name: String,
        purpose: BiometricCryptographyPurpose?,
        isUserAuthRequired: Boolean = true
    ): BiometricCryptoObject? {
        if (purpose == null) return null
        if (!isUserAuthRequired) {
            return try {
                prepareAppFlowCryptoObject(name, purpose).also { AppFlowCryptoFacade.registerKeyForAppFlow(name) }
            } catch (error: IllegalArgumentException) {
                throw error
            } catch (error: Exception) {
                throw BiometricCryptoException(error)
            }
        }
        val result = prepareBiometricCryptoObject(name, purpose)
        AppFlowCryptoFacade.registerKeyForBiometric(name)
        return result
    }

    internal fun prepareBiometricCryptoObject(name: String, purpose: BiometricCryptographyPurpose): BiometricCryptoObject {
        lock.lock()
        try {
            val cipher = when (purpose.purpose) {
                BiometricCryptographyPurpose.ENCRYPT -> getCipherForEncryption(name)
                BiometricCryptographyPurpose.DECRYPT ->
                    biometricDelegate.getInitializedCipherForDecryption(name, true, purpose.initVector)
                else -> throw IllegalArgumentException("Unsupported cryptography purpose")
            }
            return BiometricCryptoObject(
                signature = null, cipher = cipher, mac = null,
                cryptoSecurityLevel = CryptoSecurityLevel.HARDWARE_BACKED
            )
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw BiometricCryptoException(error)
        } finally {
            lock.unlock()
        }
    }

    private fun getCipherForEncryption(name: String) = try {
        biometricDelegate.getInitializedCipherForEncryption(name, true)
    } catch (error: Throwable) {
        // Only a permanently invalidated auth key may be replaced for new encryption.
        // Provider outages, locked devices and storage failures must preserve existing keys.
        if (!isPermanentlyInvalidatedKey(error)) throw error
        biometricDelegate.deleteKey(name)
        biometricDelegate.getInitializedCipherForEncryption(name, true)
    }

    /** Prepare an operation-local cipher without changing routing or holding the biometric key lock. */
    internal fun prepareAppFlowCryptoObject(name: String, purpose: BiometricCryptographyPurpose): BiometricCryptoObject {
        require(purpose.purpose == BiometricCryptographyPurpose.ENCRYPT || purpose.purpose == BiometricCryptographyPurpose.DECRYPT)
        val create = purpose.purpose == BiometricCryptographyPurpose.ENCRYPT
        val iv = if (create) null else requireNotNull(purpose.initVector) { "Initialization vector is required for decryption" }
        val allowLegacy = iv != null && AppFlowCipherFactory.isLegacyIv(iv)
        val (secret, salt) = AppFlowCryptoStorage.getKeyMaterial(name, create, allowLegacy)
        val legacySecret = if (allowLegacy) name.toCharArray().reversedArray() else null
        val cipher = try {
            if (iv == null) AppFlowCipherFactory.encrypt(secret, salt)
            else AppFlowCipherFactory.decrypt(secret, salt, iv, legacySecret)
        } finally {
            secret.fill('\u0000')
            legacySecret?.fill('\u0000')
        }
        return BiometricCryptoObject(
            signature = null, cipher = cipher, mac = null,
            cryptoSecurityLevel = CryptoSecurityLevel.APP_FLOW_NOT_BIOMETRIC_BOUND
        )
    }

}
