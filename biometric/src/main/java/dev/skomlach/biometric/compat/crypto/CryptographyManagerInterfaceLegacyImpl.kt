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

import android.util.Base64
import dev.skomlach.biometric.compat.crypto.rsa.RsaPrivateKey
import dev.skomlach.biometric.compat.crypto.rsa.RsaPublicKey
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.storage.SharedPreferenceProvider
import java.security.*
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher

class CryptographyManagerInterfaceLegacyImpl : CryptographyManagerInterface {
    private val KEYSTORE_FALLBACK_NAME: String
        get() = "biometric_keystore_fallback"
    private val PRIVATE_KEY_NAME: String
        get() = "privateKey"
    private val PUBLIC_KEY_NAME: String
        get() = "publicKey"
    private val TYPE_RSA: String
        get() = "RSA"
    private val KEY_NAME = "CryptographyManagerInterfaceLegacyImpl-$version"

    private val context = AndroidContext.appContext
    override fun deleteKey(keyName: String) {
        val sharedPreferences =
            SharedPreferenceProvider.getPreferences(
                "$KEYSTORE_FALLBACK_NAME-$keyName"
            )

        sharedPreferences.edit().clear().apply()
    }

    override fun getInitializedCipherForEncryption(
        keyName: String,
        isUserAuthRequired: Boolean
    ): Cipher {
        val cipher = getCipher()
        getOrCreateSecretKey("$KEY_NAME.$keyName")
        val keys = getPublicKeys("$KEY_NAME.$keyName")
        for (key in keys) {
            try {
                key?.let {
                    val unrestricted = KeyFactory.getInstance(key.algorithm)
                        .generatePublic(X509EncodedKeySpec(key.encoded))
                    cipher.init(Cipher.ENCRYPT_MODE, unrestricted)
                    return cipher
                }
            } catch (exception: Exception) {
            }
        }
        throw IllegalStateException("Cipher initialization error")
    }


    override fun getInitializedCipherForDecryption(
        keyName: String,
        isUserAuthRequired: Boolean,
        initializationVector: ByteArray?
    ): Cipher {
        val cipher = getCipher()
        getOrCreateSecretKey("$KEY_NAME.$keyName")
        val keys = getPrivateKeys("$KEY_NAME.$keyName")
        for (key in keys) {
            try {
                key?.let {
                    cipher.init(Cipher.DECRYPT_MODE, key)
                }
            } catch (exception: Exception) {

            }
        }
        return cipher
    }


    private fun getCipher(): Cipher {
        return Cipher.getInstance("RSA/ECB/PKCS1Padding")
    }

    @Throws(Exception::class)
    private fun getOrCreateSecretKey(name: String) {
        if (!keyExist(name)) {
            val localeBeforeFakingEnglishLocale = AndroidContext.systemLocale
            try {

                /*
             * Workaround for known date parsing issue in KeyPairGenerator class
             * https://issuetracker.google.com/issues/37095309
             * in Fabric: java.lang.IllegalArgumentException:
             * invalid date string: Unparseable date: "òððòòðòððóððGMT+00:00" (at offset 0)
             */
                setFakeEnglishLocale()
                //SK: As a fallback - generate simple RSA keypair and store keys in EncryptedSharedPreferences
                //NOTE: do not use getAlgorithmParameterSpec() - Keys cann't be stored in this case
                val keyPair = KeyPairGenerator.getInstance(TYPE_RSA)
                keyPair.initialize(2048)
                storeKeyPairInFallback(name, keyPair.generateKeyPair())
            } catch (e: Exception) {
                throw e
            } finally {
                setLocale(localeBeforeFakingEnglishLocale)
            }
        }
    }


    /**
     * Workaround for known date parsing issue in KeyPairGenerator class
     * https://issuetracker.google.com/issues/37095309
     */
    private fun setFakeEnglishLocale() {
        setLocale(Locale.US)
    }

    private fun setLocale(locale: Locale) {
        Locale.setDefault(locale)
        val resources = context.resources
        val config = resources.configuration
        config.locale = locale
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    @Throws(Exception::class)
    private fun keyExist(name: String): Boolean {
        return keyPairInFallback(name)
    }

    private fun getPrivateKeys(name: String): List<PrivateKey?> {
        val list = ArrayList<PrivateKey?>()
        getKeyPairFromFallback(name)?.let {
            list.add(it.private)
        }

        return list
    }

    private fun getPublicKeys(name: String): List<PublicKey?> {
        val list = ArrayList<PublicKey?>()
        getKeyPairFromFallback(name)?.let {
            list.add(it.public)
        }
        return list
    }

    private fun keyPairInFallback(name: String): Boolean {
        return try {
            val sharedPreferences =
                SharedPreferenceProvider.getPreferences(
                    "$KEYSTORE_FALLBACK_NAME-$name"
                )
            sharedPreferences.contains(PRIVATE_KEY_NAME) && sharedPreferences.contains(
                PUBLIC_KEY_NAME
            )
        } catch (e: Throwable) {
            false
        }

    }

    private fun getKeyPairFromFallback(name: String): KeyPair? {
        try {
            val sharedPreferences =
                SharedPreferenceProvider.getPreferences(
                    "$KEYSTORE_FALLBACK_NAME-$name"
                )
            if (sharedPreferences.contains(PRIVATE_KEY_NAME) && sharedPreferences.contains(
                    PUBLIC_KEY_NAME
                )
            ) {
                val privateKeyBytes =
                    Base64.decode(
                        sharedPreferences.getString(PRIVATE_KEY_NAME, null),
                        Base64.DEFAULT
                    )
                val publicKeyBytes =
                    Base64.decode(
                        sharedPreferences.getString(PUBLIC_KEY_NAME, null),
                        Base64.DEFAULT
                    )
                val rsaPrivateKey = RsaPrivateKey.fromByteArray(privateKeyBytes, 8)
                val rsaPublicKey = RsaPublicKey.fromByteArray(publicKeyBytes, 8)
                return KeyPair(rsaPublicKey.toRsaKey(), rsaPrivateKey.toRsaKey())
            }
        } catch (e: Throwable) {

        }
        return null
    }

    private fun storeKeyPairInFallback(name: String, keyPair: KeyPair) {
        try {
            val rsaPrivateKey = RsaPrivateKey.fromRsaKey(keyPair.private as RSAPrivateCrtKey)
            val rsaPublicKey = RsaPublicKey.fromRsaKey(keyPair.public as RSAPublicKey)
            val sharedPreferences =
                SharedPreferenceProvider.getPreferences(
                    "$KEYSTORE_FALLBACK_NAME-$name"
                )
            sharedPreferences.edit()
                .putString(
                    PRIVATE_KEY_NAME,
                    Base64.encodeToString(rsaPrivateKey.toByteArray(8), Base64.DEFAULT)
                )
                .putString(
                    PUBLIC_KEY_NAME,
                    Base64.encodeToString(rsaPublicKey.toByteArray(8), Base64.DEFAULT)
                )
                .apply()
        } catch (e: Throwable) {

        }
    }

}