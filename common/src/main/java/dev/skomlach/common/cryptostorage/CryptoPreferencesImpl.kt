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
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.security.crypto.MasterKeys
import com.securepreferences.SecurePreferences
import dev.skomlach.common.contextprovider.AndroidContext.locale
import dev.skomlach.common.logging.LogCat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class CryptoPreferencesImpl internal constructor(private val context: Context, private val name: String) :
    SharedPreferences {
    companion object {
        private const val VERSION_1: Int = 1
        private const val VERSION_2: Int = 2
        private const val CURRENT_VERSION: Int = VERSION_2

        //workaround for known date parsing issue in KeyPairGenerator
        private fun setLocale(context: Context, locale: Locale) {
            Locale.setDefault(locale)
            val resources = context.resources
            val configuration = resources.configuration
            configuration.locale = locale
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
    }

    private var cache: MutableMap<String, Any>? = null
    private var sharedPreferences: SharedPreferences? = null
        get() {
            if (field == null) {
                try {
                    field = if (CURRENT_VERSION == VERSION_2) {
                        val pref = initV2()
                        if (File(
                                ContextCompat.getDataDir(context),
                                "shared_prefs/$name.xml"
                            ).exists()
                        ) {
                            SharedPreferencesMigrationHelper.migrate(
                                context,
                                name,
                                initV1(),
                                pref
                            )
                        }
                        pref
                    } else
                        initV1()
                } catch (e: Throwable) {
                    LogCat.logException(e)
                }
                invalidateCache(field?.all)
            }

            return field
        }

    init {
        GlobalScope.launch {
            var count = 0
            var edit = sharedPreferences
            while (edit == null && count < 5) {
                count++
                edit = sharedPreferences
                Thread.sleep(100)
            }
        }
    }

    private fun initV1(): SharedPreferences {
        val defaultLocale = locale
        try {
            setLocale(context, Locale.US)
            val fallbackCheck = context.getSharedPreferences("FallbackCheck", Context.MODE_PRIVATE)
            val forceToFallback = fallbackCheck.getBoolean("forceToFallback", false)
            var pref: SharedPreferences? = null
            //AndroidX Security impl.
            //may produce exceptions on some devices (Huawei)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !forceToFallback) {
                try {
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
                    fallbackCheck.edit().putBoolean("forceToFallback", true).apply()
                }
            }

            //fallback
            if (pref == null) {
                pref = SecurePreferences(context, null, name, 5000)
            }
            return pref
        } finally {
            setLocale(context, defaultLocale)
        }
    }

    private fun initV2(): SharedPreferences {
        val defaultLocale = locale
        setLocale(context, Locale.US)
        return try {
            val masterKeyAlias =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            EncryptedSharedPreferences
                .create(
                    context,
                    "$name-EncrV2",
                    masterKeyAlias,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
        } finally {
            setLocale(context, defaultLocale)
        }
    }

    private fun invalidateCache(map: Map<String, *>?) {
        map?.let { it ->
            val tmpCache = mutableMapOf<String, Any>()
            for (k in it.keys) {
                when (val v = it[k]) {
                    is String -> {
                        tmpCache[k] = v
                    }
                    is Long -> {
                        tmpCache[k] = v
                    }
                    is Int -> {
                        tmpCache[k] = v
                    }
                    is Boolean -> {
                        tmpCache[k] = v
                    }
                    is Float -> {
                        tmpCache[k] = v
                    }
                    is Set<*> -> {
                        tmpCache[k] = HashSet(v as Set<String>)
                    }
                }
            }
            cache = mutableMapOf<String, Any>().also { cache ->
                cache.putAll(tmpCache)
            }
        }
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
            if (cache != null)
                return cache
//            if (sharedPreferences == null)
//                throw IllegalStateException("SharedPreferences not initialized")
//            else
//                return sharedPreferences?.all
        } catch (e: Throwable) {
            checkException(null, e)
            LogCat.logException(e)
        }
        return null
    }

    override fun getString(key: String, defValue: String?): String? {
        try {
            val value = cache?.get(key)
            if (value is String)
                return value
//            if (sharedPreferences == null)
//                throw IllegalStateException("SharedPreferences not initialized")
//            else
//                return sharedPreferences?.getString(key, defValue)
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return defValue
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        try {
            val value = cache?.get(key)
            if (value is Set<*>)
                return value as Set<String>

//            if (sharedPreferences == null)
//                throw IllegalStateException("SharedPreferences not initialized")
//            else
//                return sharedPreferences?.getStringSet(key, defValues)
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return defValues
    }

    override fun getInt(key: String, defValue: Int): Int {
        try {
            val value = cache?.get(key)
            if (value is Int)
                return value

//            if (sharedPreferences == null)
//                throw IllegalStateException("SharedPreferences not initialized")
//            else
//                return sharedPreferences?.getInt(key, defValue) ?: defValue
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        try {
            val value = cache?.get(key)
            if (value is Long)
                return value

//            if (sharedPreferences == null)
//                throw IllegalStateException("SharedPreferences not initialized")
//            else
//                return sharedPreferences?.getLong(key, defValue) ?: defValue
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        try {
            val value = cache?.get(key)
            if (value is Float)
                return value

//            if (sharedPreferences == null)
//                throw IllegalStateException("SharedPreferences not initialized")
//            else
//                return sharedPreferences?.getFloat(key, defValue) ?: defValue
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        try {
            val value = cache?.get(key)
            if (value is Boolean)
                return value

//            if (sharedPreferences == null)
//                throw IllegalStateException("SharedPreferences not initialized")
//            else
//                return sharedPreferences?.getBoolean(key, defValue) ?: defValue
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return defValue
    }

    override fun contains(key: String): Boolean {
        try {
            val value = cache?.get(key)
            if (value != null)
                return true

//            if (sharedPreferences == null)
//                throw IllegalStateException("SharedPreferences not initialized")
//            else
//                return sharedPreferences?.contains(key) ?: false
        } catch (e: Throwable) {
            checkException(key, e)
            LogCat.logException(e)
        }
        return false
    }

    override fun edit(): SharedPreferences.Editor {
        return CryptoEditor(this)
    }

    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        try {
            if (sharedPreferences == null)
                throw IllegalStateException("SharedPreferences not initialized")
            else
                sharedPreferences?.registerOnSharedPreferenceChangeListener(listener)
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        try {
            if (sharedPreferences == null)
                throw IllegalStateException("SharedPreferences not initialized")
            else
                sharedPreferences?.unregisterOnSharedPreferenceChangeListener(listener)
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

    private class CryptoEditor(private val cryptoPreferencesImpl: CryptoPreferencesImpl) :
        SharedPreferences.Editor {
        private val editor = cryptoPreferencesImpl.sharedPreferences?.edit()
        override fun putString(key: String, value: String?): CryptoEditor {
            return try {
                editor?.putString(key, value)
                this
            } catch (e: Throwable) {
                LogCat.logException(e)
                this
            }
        }

        override fun putStringSet(key: String, values: Set<String>?): CryptoEditor {
            return try {
                values?.let {
                    editor?.putStringSet(key, it)
                } ?: run {
                    editor?.remove(key)
                }
                this
            } catch (e: Throwable) {
                LogCat.logException(e)
                this
            }
        }

        override fun putInt(key: String, value: Int): CryptoEditor {
            return try {
                editor?.putInt(key, value)
                this
            } catch (e: Throwable) {
                LogCat.logException(e)
                this
            }
        }

        override fun putLong(key: String, value: Long): CryptoEditor {
            return try {
                editor?.putLong(key, value)
                this
            } catch (e: Throwable) {
                LogCat.logException(e)
                this
            }
        }

        override fun putFloat(key: String, value: Float): CryptoEditor {
            return try {
                editor?.putFloat(key, value)
                this
            } catch (e: Throwable) {
                LogCat.logException(e)
                this
            }
        }

        override fun putBoolean(key: String, value: Boolean): CryptoEditor {
            return try {
                editor?.putBoolean(key, value)
                this
            } catch (e: Throwable) {
                LogCat.logException(e)
                this
            }
        }

        override fun remove(key: String): CryptoEditor {
            return try {
                editor?.remove(key)
                this
            } catch (e: Throwable) {
                LogCat.logException(e)
                this
            }
        }

        override fun clear(): CryptoEditor {
            return try {
                editor?.clear()
                this
            } catch (e: Throwable) {
                LogCat.logException(e)
                this
            }
        }

        override fun commit(): Boolean {
            return try {
                editor?.commit() ?: false
            } catch (e: Throwable) {
                LogCat.logException(e)
                false
            } finally {
                cryptoPreferencesImpl.invalidateCache(cryptoPreferencesImpl.sharedPreferences?.all)
            }
        }

        override fun apply() {
            try {
                editor?.apply()
            } catch (e: Throwable) {
                LogCat.logException(e)
            } finally {
                cryptoPreferencesImpl.invalidateCache(cryptoPreferencesImpl.sharedPreferences?.all)
            }
        }
    }
}