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

import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProperties.BLOCK_MODE_CBC
import android.security.keystore.KeyProperties.ENCRYPTION_PADDING_PKCS7
import android.security.keystore.KeyProperties.KEY_ALGORITHM_AES
import android.security.keystore.KeyProperties.PURPOSE_DECRYPT
import android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
import androidx.annotation.RequiresApi
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

@RequiresApi(Build.VERSION_CODES.M)
class CryptographyManagerInterfaceMarshmallowImpl : CryptographyManagerInterface {
    private val ANDROID_KEYSTORE_PROVIDER_TYPE: String
        get() = "AndroidKeyStore"
    private val KEY_NAME = "CryptographyManagerInterfaceMarshmallowImpl-$version"
    private val keyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER_TYPE)
    }
    override fun getInitializedCipherForEncryption(
        keyName: String,
        isUserAuthRequired: Boolean,
    ): Cipher {
        return try {
            val cipher = getCipher()
            val secretKey = getOrCreateSecretKey(
                "$KEY_NAME.$keyName",
                isUserAuthRequired
            )
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(
                e,
                "KeyName=$KEY_NAME.$keyName; isUserAuthRequired=$isUserAuthRequired"
            )
            throw e
        }
    }


    override fun getInitializedCipherForDecryption(
        keyName: String,
        isUserAuthRequired: Boolean,
        initializationVector: ByteArray?
    ): Cipher {
        return try {

            val cipher = getCipher()
            val secretKey = getOrCreateSecretKey(
                "$KEY_NAME.$keyName",
                isUserAuthRequired
            )
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(initializationVector))
            cipher
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(
                e,
                "KeyName=$KEY_NAME.$keyName; isUserAuthRequired=$isUserAuthRequired"
            )
            throw e
        }
    }

    override fun deleteKey(keyName: String) {

        keyStore.load(null) // Keystore must be loaded before it can be accessed
        keyStore.deleteEntry("$KEY_NAME.$keyName")

    }

    private fun getCipher(): Cipher {
        val transformation = "$KEY_ALGORITHM_AES/$BLOCK_MODE_CBC/$ENCRYPTION_PADDING_PKCS7"
        return Cipher.getInstance(transformation)
    }

    private fun getOrCreateSecretKey(keyName: String, isUserAuthRequired: Boolean): SecretKey {
        // If Secretkey was previously created for that keyName, then grab and return it.

        keyStore.load(null) // Keystore must be loaded before it can be accessed
        keyStore.getKey(keyName, null)?.let { return it as SecretKey }

        // if you reach here, then a new SecretKey must be generated for that keyName
        val paramsBuilder = KeyGenParameterSpec.Builder(
            keyName,
            PURPOSE_ENCRYPT or PURPOSE_DECRYPT
        )
        paramsBuilder.apply {
            setBlockModes(BLOCK_MODE_CBC)
            setEncryptionPaddings(ENCRYPTION_PADDING_PKCS7)
            setRandomizedEncryptionRequired(false)
            setUserAuthenticationRequired(isUserAuthRequired)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setInvalidatedByBiometricEnrollment(isUserAuthRequired)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setUserPresenceRequired(false)//TRUE produce error during initialization
                setUserConfirmationRequired(false)//TRUE produce error during encoding
                setIsStrongBoxBacked(hasStrongBox())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                setUserAuthenticationParameters(Int.MAX_VALUE, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            else
                setUserAuthenticationValidityDurationSeconds(Int.MAX_VALUE)
        }

        val keyGenParams = paramsBuilder.build()
        val keyGenerator = KeyGenerator.getInstance(
            KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER_TYPE
        )
        keyGenerator.init(keyGenParams)
        return keyGenerator.generateKey()
    }

    private fun hasStrongBox(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            appContext.packageManager
                .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } else {
            false
        }
    }
}