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

package dev.skomlach.common.cryptostorage

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.text.TextUtils
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.securepreferences.SecurePreferences
import dev.skomlach.common.contextprovider.AndroidContext.locale
import dev.skomlach.common.logging.LogCat
import java.util.*

class CryptoPreferencesImpl internal constructor(context: Context, name: String) :
    SharedPreferences {
    /*
    * For some reasons, AndroidX Security throws exception where not should.
    * This is a "soft" workaround for this bug.
    * Bug applicable at least for androidx.security:security-crypto:1.1.0-alpha02
    * 
    * I believe that issue related to the Android KeyStore internal code -
    *  perhaps some data remains in keystore after app delete or "Clear app data" call

       "Caused by java.lang.SecurityException: Could not decrypt value. decryption failed
       at androidx.security.crypto.EncryptedSharedPreferences.getDecryptedObject(EncryptedSharedPreferences.java:580)
       at androidx.security.crypto.EncryptedSharedPreferences.getLong(EncryptedSharedPreferences.java:437)"
    *
    */

    companion object {
        //workaround for known date parsing issue in KeyPairGenerator
        private fun setLocale(context: Context, locale: Locale) {
            Locale.setDefault(locale)
            val resources = context.resources
            val configuration = resources.configuration
            configuration.locale = locale
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
    }

    private var sharedPreferences: SharedPreferences

    init {
        val defaultLocale = locale
        val fallbackCheck = context.getSharedPreferences("FallbackCheck", Context.MODE_PRIVATE)
        val forceToFallback = fallbackCheck.getBoolean("forceToFallback", false)
        var pref: SharedPreferences? = null
        //AndroidX Security impl.
        //may produce exceptions on some devices (Huawei)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !forceToFallback) {
            try {
                setLocale(context, Locale.US)
                val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
                val masterKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)
                pref = EncryptedSharedPreferences
                    .create(
                        name,
                        masterKeyAlias,
                        context,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
            } catch (e: Exception) {
                pref = null
                setLocale(context, defaultLocale)
                fallbackCheck.edit().putBoolean("forceToFallback", true).apply()
            }
        }

        //fallback
        if (pref == null) {
            setLocale(context, Locale.US)
            pref = SecurePreferences(context, null, name, 5000)
        }
        setLocale(context, defaultLocale)
        sharedPreferences = pref
    }

    private fun checkAndDeleteIfNeed(key: String?, e: Throwable): Boolean {
        if (!e.toString().contains("Could not decrypt value")) {
            if (!key.isNullOrEmpty()) {
                LogCat.log("Remove broken value for key '$key'")
                edit().remove(key).apply()
            } else {
                LogCat.log("Remove all broken values")
                edit().clear().apply()
            }
            return true
        }
        return false
    }

    private fun checkException(key: String?, e: Throwable?) {
        if (e == null || checkAndDeleteIfNeed(key, e)) {
            return
        }
        checkException(key, e.cause)
    }

    override fun getAll(): Map<String, *>? {
        try {
            return sharedPreferences.all
        } catch (e: Throwable) {
            checkException(null, e)
            LogCat.logException(e)
        }
        return null
    }

    override fun getString(key: String, defValue: String?): String? {
        try {
            return sharedPreferences.getString(key, defValue)
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return defValue
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        try {
            return sharedPreferences.getStringSet(key, defValues)
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return defValues
    }

    override fun getInt(key: String, defValue: Int): Int {
        try {
            return sharedPreferences.getInt(key, defValue)
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        try {
            return sharedPreferences.getLong(key, defValue)
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        try {
            return sharedPreferences.getFloat(key, defValue)
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        try {
            return sharedPreferences.getBoolean(key, defValue)
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return defValue
    }

    override fun contains(key: String): Boolean {
        try {
            return sharedPreferences.contains(key)
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return false
    }

    override fun edit(): SharedPreferences.Editor {
        return CryptoEditor(sharedPreferences.edit())
    }

    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        try {
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        try {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

    private class CryptoEditor(private val editor: SharedPreferences.Editor) :
        SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            return try {
                CryptoEditor(editor.putString(key, value))
            } catch (e: Throwable) {
                LogCat.logException(e)
                editor
            }
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            return try {
                CryptoEditor(editor.putStringSet(key, values))
            } catch (e: Throwable) {
                LogCat.logException(e)
                editor
            }
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            return try {
                CryptoEditor(editor.putInt(key, value))
            } catch (e: Throwable) {
                LogCat.logException(e)
                editor
            }
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            return try {
                CryptoEditor(editor.putLong(key, value))
            } catch (e: Throwable) {
                LogCat.logException(e)
                editor
            }
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            return try {
                CryptoEditor(editor.putFloat(key, value))
            } catch (e: Throwable) {
                LogCat.logException(e)
                editor
            }
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            return try {
                CryptoEditor(editor.putBoolean(key, value))
            } catch (e: Throwable) {
                LogCat.logException(e)
                editor
            }
        }

        override fun remove(key: String): SharedPreferences.Editor {
            return try {
                CryptoEditor(editor.remove(key))
            } catch (e: Throwable) {
                LogCat.logException(e)
                editor
            }
        }

        override fun clear(): SharedPreferences.Editor {
            return try {
                CryptoEditor(editor.clear())
            } catch (e: Throwable) {
                LogCat.logException(e)
                editor
            }
        }

        override fun commit(): Boolean {
            return try {
                editor.commit()
            } catch (e: Throwable) {
                LogCat.logException(e)
                false
            }
        }

        override fun apply() {
            try {
                editor.apply()
            } catch (e: Throwable) {
                LogCat.logException(e)
            }
        }
    }
}