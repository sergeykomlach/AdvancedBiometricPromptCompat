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

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.security.KeyPairGeneratorSpec
import androidx.annotation.RequiresApi
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.currentLocale
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import javax.crypto.Cipher
import javax.security.auth.x500.X500Principal

@RequiresApi(Build.VERSION_CODES.KITKAT)
class CryptographyManagerInterfaceKitkatImpl : CryptographyManagerInterface {
    override val version: String
        get() = "v2"
    private val TYPE_RSA: String
        get() = "RSA"
    private val ANDROID_KEYSTORE_PROVIDER_TYPE: String
        get() = "AndroidKeyStore"
    private val context = AndroidContext.appContext

    private val KEY_NAME = "CryptographyManagerInterfaceKitkatImpl-$version"
    private val keyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER_TYPE)
    }

    override fun deleteKey(keyName: String) {

        keyStore.load(null) // Keystore must be loaded before it can be accessed
        keyStore.deleteEntry("$KEY_NAME.$keyName")
    }

    override fun getInitializedCipherForEncryption(
        keyName: String,
        isUserAuthRequired: Boolean
    ): Cipher {
        try {
            val cipher = getCipher()
            getOrCreateSecretKey("$KEY_NAME.$keyName")
            val key = getPublicKey("$KEY_NAME.$keyName")
                ?: throw IllegalStateException("Cipher initialization error")
            val unrestricted = KeyFactory.getInstance(key.algorithm)
                .generatePublic(X509EncodedKeySpec(key.encoded))
            cipher.init(Cipher.ENCRYPT_MODE, unrestricted)
            return cipher
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
        try {
            val cipher = getCipher()
            getOrCreateSecretKey("$KEY_NAME.$keyName")
            val key = getPrivateKey("$KEY_NAME.$keyName")
                ?: throw IllegalStateException("Cipher initialization error")
            cipher.init(Cipher.DECRYPT_MODE, key)
            return cipher
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(
                e,
                "KeyName=$KEY_NAME.$keyName; isUserAuthRequired=$isUserAuthRequired"
            )
            throw e
        }
    }

    private fun getCipher(): Cipher {

        return Cipher.getInstance("RSA/ECB/PKCS1Padding")

    }

    @Throws(Exception::class)
    private fun getOrCreateSecretKey(name: String) {

        if (!keyExist(name)) {
            try {
                val keyPairGenerator = KeyPairGenerator
                    .getInstance(
                        TYPE_RSA,
                        ANDROID_KEYSTORE_PROVIDER_TYPE
                    )
                val spec = getAlgorithmParameterSpec(name)

                keyPairGenerator.initialize(spec)

                keyPairGenerator.generateKeyPair()//SK: Exception on some devices here; It seems like device-specific KeyStore issue
            } catch (e: IllegalStateException) {
                throw IllegalStateException(
                    "Android Keystore is unavailable; insecure software fallback has been disabled",
                    e
                )
            } catch (e: Exception) {
                throw e
            }
        }


    }


    @SuppressLint("WrongConstant")
    private fun getAlgorithmParameterSpec(name: String): AlgorithmParameterSpec {
        val keySize = 2048
        val subject = X500Principal("CN=${name}")
        val serialNumber = BigInteger.valueOf(1337)
        val localeBeforeFakingEnglishLocale = AndroidContext.systemLocale
        //SK: See https://doridori.github.io/android-security-the-forgetful-keystore/
        try {
            return KeyPairGeneratorSpec.Builder(updateLocale(context))
                .setAlias(name)
                .setKeySize(keySize)
                .setSubject(subject)
                .setSerialNumber(serialNumber)
                .build()
        } finally {
            updateLocale(context, localeBeforeFakingEnglishLocale)
        }

    }

    /**
     * Workaround for known date parsing issue in KeyPairGenerator class
     * https://issuetracker.google.com/issues/37095309
     */
    private fun updateLocale(context: Context, locale: Locale = Locale.US): Context {
        val res = context.resources
        val current = res.configuration.currentLocale
        if (current == locale) return context
        val config = Configuration(res.configuration)
        ConfigurationCompat.setLocales(config, LocaleListCompat.create(locale))
        val ctx = if (Build.VERSION.SDK_INT >= 17) {
            context.createConfigurationContext(config)
        } else {
            config.locale = locale
            res.updateConfiguration(config, res.displayMetrics)
            context
        }
        return ctx
    }

    @Throws(Exception::class)
    private fun keyExist(name: String): Boolean {
        keyStore.load(null)
        return keyStore.containsAlias(name)
    }

    private fun getPrivateKey(name: String) =
        keyStore.getKey(name, null) as? java.security.PrivateKey

    private fun getPublicKey(name: String) = keyStore.getCertificate(name)?.publicKey

}