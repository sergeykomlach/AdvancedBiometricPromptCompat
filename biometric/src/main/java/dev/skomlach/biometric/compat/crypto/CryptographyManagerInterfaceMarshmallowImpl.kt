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
import android.util.Base64
import androidx.annotation.RequiresApi
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.storage.SharedPreferenceProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RequiresApi(Build.VERSION_CODES.M)
class CryptographyManagerInterfaceMarshmallowImpl : CryptographyManagerInterface {
    private val KEYSTORE_FALLBACK_NAME: String
        get() = "biometric_keystore_fallback"
    private val VECTOR_NAME: String
        get() = "vectorName"
    private val ANDROID_KEYSTORE_PROVIDER_TYPE: String
        get() = "AndroidKeyStore"
    private val KEY_SIZE: Int = 256

    override fun getInitializedCipherForEncryption(
        keyName: String,
        isUserAuthRequired: Boolean
    ): Cipher {
        return try {
            val cipher = getCipher()
            val secretKey = getOrCreateSecretKey(
                "CryptographyManagerInterfaceMarshmallowImpl.$keyName",
                isUserAuthRequired
            )
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(
                e,
                "KeyName=CryptographyManagerInterfaceMarshmallowImpl.$keyName; isUserAuthRequired=$isUserAuthRequired"
            )
            throw e
        }
    }


    override fun getInitializedCipherForDecryption(
        keyName: String,
        isUserAuthRequired: Boolean
    ): Cipher {
        return try {
            val initializationVector = getKeyPairFromFallback(keyName)
                ?: throw IllegalStateException("Initial vector is null")
            val cipher = getCipher()
            val secretKey = getOrCreateSecretKey(
                "CryptographyManagerInterfaceMarshmallowImpl.$keyName",
                isUserAuthRequired
            )
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, initializationVector))
            cipher
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(
                e,
                "KeyName=CryptographyManagerInterfaceMarshmallowImpl.$keyName; isUserAuthRequired=$isUserAuthRequired"
            )
            throw e
        }
    }

    private fun getCipher(): Cipher {
        val transformation = "$KEY_ALGORITHM_AES/$BLOCK_MODE_GCM/$ENCRYPTION_PADDING_NONE"
        return Cipher.getInstance(transformation)
    }

    private fun getOrCreateSecretKey(keyName: String, isUserAuthRequired: Boolean): SecretKey {
        // If Secretkey was previously created for that keyName, then grab and return it.
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER_TYPE)
        keyStore.load(null) // Keystore must be loaded before it can be accessed
        keyStore.getKey(keyName, null)?.let { return it as SecretKey }

        // if you reach here, then a new SecretKey must be generated for that keyName
        val paramsBuilder = KeyGenParameterSpec.Builder(
            keyName,
            PURPOSE_ENCRYPT or PURPOSE_DECRYPT
        )
        paramsBuilder.apply {
            setBlockModes(BLOCK_MODE_GCM)
            setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
            setKeySize(KEY_SIZE)
            setUserAuthenticationRequired(isUserAuthRequired)
        }

        val keyGenParams = paramsBuilder.build()
        val keyGenerator = KeyGenerator.getInstance(
            KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER_TYPE
        )
        keyGenerator.init(keyGenParams)
        return keyGenerator.generateKey()
    }

    private fun getKeyPairFromFallback(name: String): ByteArray? {
        try {
            val sharedPreferences =
                SharedPreferenceProvider.getPreferences(
                    "$KEYSTORE_FALLBACK_NAME-CryptographyManagerInterfaceMarshmallowImpl.$name"
                )

            return Base64.decode(
                sharedPreferences.getString(VECTOR_NAME, null),
                Base64.DEFAULT
            )

        } catch (e: Throwable) {
            BiometricLoggerImpl.e(
                e,
                "KeyName=CryptographyManagerInterfaceMarshmallowImpl.$name"
            )
        }
        return null
    }

    fun storeKeyPairInFallback(name: String, vector: ByteArray) {

        try {
            val sharedPreferences =
                SharedPreferenceProvider.getPreferences(
                    "$KEYSTORE_FALLBACK_NAME-CryptographyManagerInterfaceMarshmallowImpl.$name"
                )
            sharedPreferences.edit()
                .putString(
                    VECTOR_NAME,
                    Base64.encodeToString(vector, Base64.DEFAULT)
                )
                .apply()
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(
                e,
                "KeyName=CryptographyManagerInterfaceMarshmallowImpl.$name"
            )
        }
    }
}