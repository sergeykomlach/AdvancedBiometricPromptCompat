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

import android.annotation.SuppressLint
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.util.Base64
import androidx.annotation.RequiresApi
import dev.skomlach.biometric.compat.crypto.rsa.RsaPrivateKey
import dev.skomlach.biometric.compat.crypto.rsa.RsaPublicKey
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.storage.SharedPreferenceProvider
import java.math.BigInteger
import java.security.*
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.security.auth.x500.X500Principal

@RequiresApi(Build.VERSION_CODES.KITKAT)
class CryptographyManagerLegacyImpl : CryptographyManager {
    companion object {
        private const val KEYSTORE_FALLBACK_NAME = "biometric_keystore_fallback"
        private const val PRIVATE_KEY_NAME = "privateKey"
        private const val PUBLIC_KEY_NAME = "publicKey"
        private const val TYPE_RSA = "RSA"
        private const val ANDROID_KEYSTORE_PROVIDER_TYPE = "AndroidKeyStore"
    }

    private val context = AndroidContext.appContext

    override fun getInitializedCipherForEncryption(keyName: String): Cipher {
        val cipher = getCipher()
        getOrCreateSecretKey(keyName)
        val keys = getPublicKeys(keyName)
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
        initializationVector: ByteArray?
    ): Cipher {
        val cipher = getCipher()
        getOrCreateSecretKey(keyName)
        val keys = getPrivateKeys(keyName)
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

    override fun encryptData(plaintext: ByteArray, cipher: Cipher): EncryptedData {
        return EncryptedData(cipher.doFinal(plaintext), cipher.iv)
    }

    override fun decryptData(ciphertext: ByteArray, cipher: Cipher): ByteArray {
        return cipher.doFinal(ciphertext)
    }

    private fun getCipher(): Cipher {
        return Cipher.getInstance("RSA/ECB/PKCS1Padding")
    }

    @Throws(Exception::class)
    private fun getOrCreateSecretKey(name: String) {
        if (!keyExist(name)) {
            val localeBeforeFakingEnglishLocale = Locale.getDefault()
            try {

                /*
             * Workaround for known date parsing issue in KeyPairGenerator class
             * https://issuetracker.google.com/issues/37095309
             * in Fabric: java.lang.IllegalArgumentException:
             * invalid date string: Unparseable date: "òððòòðòððóððGMT+00:00" (at offset 0)
             */
                setFakeEnglishLocale()

                val keyPairGenerator = KeyPairGenerator
                    .getInstance(
                        TYPE_RSA,
                        ANDROID_KEYSTORE_PROVIDER_TYPE
                    )
                val spec = getAlgorithmParameterSpec(name)

                keyPairGenerator.initialize(spec)

                val keyPair =
                    keyPairGenerator.generateKeyPair()//SK: Exception on some devices here; It seems like device-specific KeyStore issue
            } catch (e: IllegalStateException) {
                //SK: As a fallback - generate simple RSA keypair and store keys in EncryptedSharedPreferences
                //NOTE: do not use getAlgorithmParameterSpec() - Keys cann't be stored in this case
                val keyPair = KeyPairGenerator.getInstance(TYPE_RSA)
                keyPair.initialize(2048)
                storeKeyPairInFallback(keyPair.generateKeyPair())
            } catch (e: Exception) {
                throw e
            } finally {
                setLocale(localeBeforeFakingEnglishLocale)
            }
        }
    }


    @SuppressLint("WrongConstant")
    private fun getAlgorithmParameterSpec(KEY_ALIAS: String): AlgorithmParameterSpec {
        val keySize = 2048
        val subject = X500Principal("CN=${KEY_ALIAS}")
        val serialNumber = BigInteger.valueOf(1337)
        val validityStartCalendar = Calendar.getInstance()
        val validityEndCalendar = Calendar.getInstance()
        validityEndCalendar.add(Calendar.YEAR, Int.MAX_VALUE)
        val validityStartDate = validityStartCalendar.time
        val validityEndDate = validityEndCalendar.time
        //SK: See https://doridori.github.io/android-security-the-forgetful-keystore/
        return KeyPairGeneratorSpec.Builder(context)
            .setAlias(KEY_ALIAS)
            .setKeySize(keySize)
            .setSubject(subject)
            .setSerialNumber(serialNumber)
            .setStartDate(validityStartDate)
            .setEndDate(validityEndDate)
            .build()

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
    private fun keyExist(KEY_ALIAS: String): Boolean {
        var entry = keyPairInFallback()
        try {

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER_TYPE)

            keyStore.load(null)

            entry = entry || keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Throwable) {

        }
        return entry
    }

    private fun getPrivateKeys(KEY_ALIAS: String): List<PrivateKey?> {
        val list = ArrayList<PrivateKey?>()

        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER_TYPE)
            keyStore.load(null)
            list.add(keyStore.getKey(KEY_ALIAS, null) as PrivateKey?)
        } catch (e: Throwable) {

        }
        getKeyPairFromFallback()?.let {
            list.add(it.private)
        }

        return list
    }

    private fun getPublicKeys(KEY_ALIAS: String): List<PublicKey?> {
        val list = ArrayList<PublicKey?>()
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER_TYPE)
            keyStore.load(null)
            list.add(keyStore.getCertificate(KEY_ALIAS)?.publicKey)
        } catch (e: Throwable) {

        }
        getKeyPairFromFallback()?.let {
            list.add(it.public)
        }
        return list
    }

    private fun keyPairInFallback(): Boolean {
        return try {
            val sharedPreferences =
                SharedPreferenceProvider.getPreferences(
                    KEYSTORE_FALLBACK_NAME
                )
            sharedPreferences.contains(PRIVATE_KEY_NAME) && sharedPreferences.contains(
                PUBLIC_KEY_NAME
            )
        } catch (e: Throwable) {
            false
        }

    }

    private fun getKeyPairFromFallback(): KeyPair? {
        try {
            val sharedPreferences =
                SharedPreferenceProvider.getPreferences(
                    KEYSTORE_FALLBACK_NAME
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

    private fun storeKeyPairInFallback(keyPair: KeyPair) {
        try {
            val rsaPrivateKey = RsaPrivateKey.fromRsaKey(keyPair.private as RSAPrivateCrtKey)
            val rsaPublicKey = RsaPublicKey.fromRsaKey(keyPair.public as RSAPublicKey)
            val sharedPreferences =
                SharedPreferenceProvider.getPreferences(
                    KEYSTORE_FALLBACK_NAME
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