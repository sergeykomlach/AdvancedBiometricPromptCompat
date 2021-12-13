/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package androidx.security.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme
import androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme
import androidx.security.crypto.MasterKey.KEYSTORE_PATH_URI
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dev.skomlach.common.logging.LogCat
import java.io.IOException
import java.lang.reflect.Modifier
import java.security.GeneralSecurityException

object EncryptedSharedPreferencesWorkaround {

    private const val KEY_KEYSET_ALIAS =
        "__androidx_security_crypto_encrypted_prefs_key_keyset__"
    private const val VALUE_KEYSET_ALIAS =
        "__androidx_security_crypto_encrypted_prefs_value_keyset__"

    /**
     * Opens an instance of encrypted SharedPreferences
     *
     * @param fileName                  The name of the file to open; can not contain path
     * separators.
     * @param masterKey                 The master key to use.
     * @param prefKeyEncryptionScheme   The scheme to use for encrypting keys.
     * @param prefValueEncryptionScheme The scheme to use for encrypting values.
     * @return The SharedPreferences instance that encrypts all data.
     * @throws GeneralSecurityException when a bad master key or keyset has been attempted
     * @throws IOException              when fileName can not be used
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun create(
        context: Context,
        fileName: String,
        masterKey: MasterKey,
        prefKeyEncryptionScheme: PrefKeyEncryptionScheme,
        prefValueEncryptionScheme: PrefValueEncryptionScheme
    ): SharedPreferences {
        return create(
            fileName, masterKey.keyAlias, context,
            prefKeyEncryptionScheme, prefValueEncryptionScheme
        )
    }

    /**
     * Opens an instance of encrypted SharedPreferences
     *
     * @param fileName                  The name of the file to open; can not contain path
     *                                  separators.
     * @param masterKeyAlias            The alias of the master key to use.
     * @param context                   The context to use to open the preferences file.
     * @param prefKeyEncryptionScheme   The scheme to use for encrypting keys.
     * @param prefValueEncryptionScheme The scheme to use for encrypting values.
     * @return The SharedPreferences instance that encrypts all data.
     * @throws GeneralSecurityException when a bad master key or keyset has been attempted
     * @throws IOException              when fileName can not be used
     * @deprecated Use {@link #create(Context, String, MasterKey,
     * PrefKeyEncryptionScheme, PrefValueEncryptionScheme)} instead.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun create(
        fileName: String,
        masterKeyAlias: String,
        context: Context,
        prefKeyEncryptionScheme: PrefKeyEncryptionScheme,
        prefValueEncryptionScheme: PrefValueEncryptionScheme
    ): SharedPreferences {
        DeterministicAeadConfig.register()
        AeadConfig.register()
        val applicationContext = context.applicationContext
        val daeadKeysetHandle = AndroidKeysetManager.Builder()
            .withKeyTemplate(prefKeyEncryptionScheme.keyTemplate)
            .withSharedPref(
                applicationContext,
                KEY_KEYSET_ALIAS,
                fileName
            )
            .withMasterKeyUri(KEYSTORE_PATH_URI + masterKeyAlias)
            .doNotUseKeystore()//Disable AndroidKeystore
            .build().keysetHandle
        val aeadKeysetHandle = AndroidKeysetManager.Builder()
            .withKeyTemplate(prefValueEncryptionScheme.keyTemplate)
            .withSharedPref(
                applicationContext,
                VALUE_KEYSET_ALIAS,
                fileName
            )
            .withMasterKeyUri(KEYSTORE_PATH_URI + masterKeyAlias)
            .doNotUseKeystore()//Disable AndroidKeystore
            .build().keysetHandle
        val daead = daeadKeysetHandle.getPrimitive(
            DeterministicAead::class.java
        )
        val aead = aeadKeysetHandle.getPrimitive(Aead::class.java)
        return EncryptedSharedPreferences(
            fileName, masterKeyAlias,
            applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE), aead,
            daead
        )
    }

    private fun decryptKey(pref: EncryptedSharedPreferences, key: String): String {
        return pref.decryptKey(key)
    }

    private fun decryptObject(pref: EncryptedSharedPreferences, key: String): Any? {
        val method = EncryptedSharedPreferences::class.java.declaredMethods.firstOrNull {
            Modifier.isPrivate(it.modifiers) &&
                    it.returnType == Object::class.java &&
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
        }
        val isAccessible = method?.isAccessible
        try {
            if (isAccessible == false)
                method.isAccessible = true
            return method?.invoke(pref, key)
        } finally {
            if (isAccessible == false)
                method.isAccessible = false
        }
    }

    fun getAll(sharedPreferences: SharedPreferences?): Map<String, *>? {
        try {
            if (sharedPreferences == null)
                throw IllegalStateException("SharedPreferences not initialized")
            else
                return sharedPreferences.all
        } catch (e: Throwable) {
            //workaround
            sharedPreferences?.let {
                if (it is EncryptedSharedPreferences)
                    try {
                        val all = it.mSharedPreferences.all ?: emptyMap()
                        val map = mutableMapOf<String, Any?>()
                        for (encryptedKey in all.keys) {
                            try {
                                if (it.isReservedKey(encryptedKey))
                                    continue

                                val plaintextKey = decryptKey(
                                    it,
                                    encryptedKey
                                )
                                map[plaintextKey] = null
                                val plaintextValue =
                                    decryptObject(
                                        it,
                                        plaintextKey
                                    )
                                map[plaintextKey] = plaintextValue
                            } catch (ignore: Throwable) {
                                LogCat.logException(ignore)
                            }
                        }
                        return map
                    } catch (ex: Throwable) {
                        LogCat.logException(ex)
                    }
            }
            LogCat.logException(e)
        }
        return null
    }
}