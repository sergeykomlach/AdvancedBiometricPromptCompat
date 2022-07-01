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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties.*
import androidx.annotation.RequiresApi
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RequiresApi(Build.VERSION_CODES.M)
class CryptographyManagerInterfaceMarshmallowImpl : CryptographyManagerInterfaceKitkatImpl() {
    override fun getInitializedCipherForEncryption(
        keyName: String,
        isUserAuthRequired: Boolean
    ): Cipher {
        return try {
            val cipher = getCipher()
            val secretKey = getOrCreateSecretKey(keyName, isUserAuthRequired)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
            super.getInitializedCipherForEncryption(keyName, isUserAuthRequired)
        }
    }


    override fun getInitializedCipherForDecryption(
        keyName: String,
        isUserAuthRequired: Boolean
    ): Cipher {
        return try {
            val cipher = getCipher()
            val secretKey = getOrCreateSecretKey(keyName, isUserAuthRequired)
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            cipher
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
            super.getInitializedCipherForDecryption(
                keyName,
                isUserAuthRequired
            )
        }
    }

    private fun getCipher(): Cipher {
        val transformation = "$KEY_ALGORITHM_AES/$BLOCK_MODE_CBC/$ENCRYPTION_PADDING_PKCS7"
        return Cipher.getInstance(transformation)
    }

    private fun getOrCreateSecretKey(keyName: String, isUserAuthRequired: Boolean): SecretKey {

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER_TYPE)

        // If Secretkey was previously created for that keyName, then grab and return it.
        keyStore.load(null) // Keystore must be loaded before it can be accessed
        val key = keyStore.getKey(keyName, null)
        if(key is SecretKey)
            return key
        // if you reach here, then a new SecretKey must be generated for that keyName
        val paramsBuilder = KeyGenParameterSpec.Builder(
            keyName,
            PURPOSE_ENCRYPT or PURPOSE_DECRYPT
        )
        paramsBuilder.apply {
            setBlockModes(BLOCK_MODE_CBC)
            setEncryptionPaddings(ENCRYPTION_PADDING_PKCS7)
            setUserAuthenticationRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setIsStrongBoxBacked(true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setInvalidatedByBiometricEnrollment(isUserAuthRequired)
            }


        }

        val keyGenParams = paramsBuilder.build()
        val keyGenerator = KeyGenerator.getInstance(
            KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER_TYPE
        )
        keyGenerator.init(keyGenParams)
        return keyGenerator.generateKey()
    }

}