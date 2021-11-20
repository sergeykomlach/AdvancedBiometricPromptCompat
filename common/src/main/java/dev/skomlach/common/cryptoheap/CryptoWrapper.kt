/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.common.cryptoheap

import android.content.Context
import android.os.Build
import androidx.security.crypto.MasterKeys
import com.securepreferences.SecurePreferences
import com.tozny.crypto.android.AesCbcWithIntegrity.SecretKeys
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.contextprovider.AndroidContext.locale
import dev.skomlach.common.logging.LogCat
import java.io.Serializable
import java.security.KeyStore
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SealedObject
import javax.crypto.SecretKey

class CryptoWrapper private constructor() {
    private var secretKey: SecretKey? = null
    private var TRANSFORMATION: String? = null

    companion object {
        var INSTANCE = CryptoWrapper()

        //workaround for known date parsing issue in KeyPairGenerator
        private fun setLocale(context: Context, locale: Locale) {
            Locale.setDefault(locale)
            val resources = context.resources
            val configuration = resources.configuration
            configuration.locale = locale
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
    }

    init {
        val defaultLocale = locale

        val fallbackCheck =
            appContext.getSharedPreferences("FallbackCheck", Context.MODE_PRIVATE)
        val forceToFallback = fallbackCheck.getBoolean("forceToFallback", false)
        //AndroidX Security impl.
        //may produce exceptions on some devices (Huawei)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !forceToFallback) {
            try {
                setLocale(appContext, Locale.US)
                val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
                val masterKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                secretKey = keyStore.getKey(masterKeyAlias, null) as SecretKey
                TRANSFORMATION = "AES/GCM/NoPadding"
            } catch (e: Exception) {
                secretKey = null
                setLocale(appContext, defaultLocale)
                fallbackCheck.edit().putBoolean("forceToFallback", true).apply()
            }
        }


        if (secretKey == null) {
            try {
                setLocale(appContext, Locale.US)
                //fallback for L and older
                val securePreferences = SecurePreferences(appContext, 5000)
                val field = SecurePreferences::class.java.declaredFields.firstOrNull {
                    it.type == SecretKeys::class.java
                }
                val isAccessible = field?.isAccessible ?: true
                if (!isAccessible) {
                    field?.isAccessible = true
                }
                val keys = field?.let { it[securePreferences] as SecretKeys } ?: kotlin.run { null }
                if (!isAccessible) {
                    field?.isAccessible = false
                }
                secretKey = keys?.confidentialityKey
                TRANSFORMATION = "AES/CBC/PKCS5Padding"
            } catch (e: Throwable) {
                LogCat.logException(e)
                setLocale(appContext, defaultLocale)
                secretKey = null
            }
        }
    }

    fun wrapObject(obj: Serializable?): SealedObject? {
        return try {
            if (obj == null) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            SealedObject(obj, cipher)
        } catch (e: Throwable) {
            throw RuntimeException(e)
        }
    }

    fun unwrapObject(sealedObject: SealedObject?): Serializable? {
        return try {
            if (sealedObject == null) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            sealedObject.getObject(cipher) as Serializable
        } catch (e: Throwable) {
            throw RuntimeException(e)
        }
    }
}