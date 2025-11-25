/*
 *  Copyright (c) 2025 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.common.storage

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Pair
import androidx.collection.ArraySet
import com.tozny.crypto.android.AesCbcWithIntegrity
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.text.Charsets.UTF_8

class EncryptedSharedPreferences(
    private val context: Context,
    private val sharedPrefFilename: String? = null
) : SharedPreferences {
    companion object {
        private const val NULL_VALUE = "__NULL__"
    }

    private val keys by lazy {
        val config = SharedPreferenceProvider.EncryptionConfig.instance
        AesCbcWithIntegrity.generateKeyFromPassword(
            String(config.password.reversedArray()),
            config.salt.reversedArray()
        )
    }

    private val mListeners =
        CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()


    //the backing pref file
    private var mSharedPreferences: SharedPreferences = if (sharedPrefFilename.isNullOrEmpty()) {
        PreferenceManager.getDefaultSharedPreferences(context)
    } else {
        context.getSharedPreferences(sharedPrefFilename, Context.MODE_PRIVATE)
    }

    /**
     * @param ciphertext
     * @return decrypted plain text, unless decryption fails, in which case null
     */
    private fun decryptString(ciphertext: String?): String? {
        if (ciphertext.isNullOrEmpty()) {
            return ciphertext
        }
        return null
    }

    private fun encryptString(cleartext: String?): String? {
        if (cleartext.isNullOrEmpty()) {
            return cleartext
        }
        return null
    }

    private fun decrypt(ciphertext: String?): ByteArray? {
        if (ciphertext.isNullOrEmpty()) {
            return ciphertext?.toByteArray(UTF_8)
        }
        try {
            val cipherTextIvMac: AesCbcWithIntegrity.CipherTextIvMac =
                AesCbcWithIntegrity.CipherTextIvMac(ciphertext)
            return AesCbcWithIntegrity.decrypt(cipherTextIvMac, keys)
        } catch (e: GeneralSecurityException) {
        } catch (e: UnsupportedEncodingException) {
        }
        return null
    }

    private fun encrypt(cleartext: ByteArray?): String? {
        if (cleartext == null || cleartext.isEmpty()) {
            return String(cleartext ?: return null, UTF_8)
        }
        try {
            return AesCbcWithIntegrity.encrypt(cleartext, keys).toString()
        } catch (e: GeneralSecurityException) {
        } catch (e: UnsupportedEncodingException) {
        }
        return null
    }

    private class Editor(
        private val mEncryptedSharedPreferences: EncryptedSharedPreferences,
        editor: SharedPreferences.Editor
    ) : SharedPreferences.Editor {
        private val mEditor: SharedPreferences.Editor = editor
        private val mKeysChanged: MutableList<String?> = CopyOnWriteArrayList()
        private val mClearRequested = AtomicBoolean(false)

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            var value = value
            if (value == null) {
                value = NULL_VALUE
            }
            val stringBytes = value.toByteArray(StandardCharsets.UTF_8)
            val stringByteLength = stringBytes.size
            val buffer = ByteBuffer.allocate(
                (Integer.BYTES + Integer.BYTES + stringByteLength)
            )
            buffer.putInt(EncryptedType.STRING.id)
            buffer.putInt(stringByteLength)
            buffer.put(stringBytes)
            putEncryptedObject(key, buffer.array())
            return this
        }

        override fun putStringSet(
            key: String?, values: MutableSet<String>?
        ): SharedPreferences.Editor {
            var values = values
            if (values == null) {
                values = ArraySet()
                values.add(NULL_VALUE)
            }
            val byteValues: MutableList<ByteArray> = ArrayList(values.size)
            var totalBytes = values.size * Integer.BYTES
            for (strValue in values) {
                val byteValue = strValue.toByteArray(StandardCharsets.UTF_8)
                byteValues.add(byteValue)
                totalBytes += byteValue.size
            }
            totalBytes += Integer.BYTES
            val buffer = ByteBuffer.allocate(totalBytes)
            buffer.putInt(EncryptedType.STRING_SET.id)
            for (bytes in byteValues) {
                buffer.putInt(bytes.size)
                buffer.put(bytes)
            }
            putEncryptedObject(key, buffer.array())
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            val buffer = ByteBuffer.allocate(Integer.BYTES + Integer.BYTES)
            buffer.putInt(EncryptedType.INT.id)
            buffer.putInt(value)
            putEncryptedObject(key, buffer.array())
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            val buffer = ByteBuffer.allocate(Integer.BYTES + java.lang.Long.BYTES)
            buffer.putInt(EncryptedType.LONG.id)
            buffer.putLong(value)
            putEncryptedObject(key, buffer.array())
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            val buffer = ByteBuffer.allocate(Integer.BYTES + java.lang.Float.BYTES)
            buffer.putInt(EncryptedType.FLOAT.id)
            buffer.putFloat(value)
            putEncryptedObject(key, buffer.array())
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            val buffer = ByteBuffer.allocate(Integer.BYTES + java.lang.Byte.BYTES)
            buffer.putInt(EncryptedType.BOOLEAN.id)
            buffer.put(if (value) 1.toByte() else 0.toByte())
            putEncryptedObject(key, buffer.array())
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            mEditor.remove(mEncryptedSharedPreferences.encryptKey(key))
            mKeysChanged.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            // Set the flag to clear on commit, this operation happens first on commit.
            // Cannot use underlying clear operation, it will remove the keysets and
            // break the editor.
            mClearRequested.set(true)
            return this
        }

        override fun commit(): Boolean {
            clearKeysIfNeeded()
            try {
                return mEditor.commit()
            } finally {
                notifyListeners()
                mKeysChanged.clear()
            }
        }

        override fun apply() {
            clearKeysIfNeeded()
            mEditor.apply()
            notifyListeners()
            mKeysChanged.clear()
        }

        fun clearKeysIfNeeded() {
            // Call "clear" first as per the documentation, remove all keys that haven't
            // been modified in this editor.
            if (mClearRequested.getAndSet(false)) {
                for (key in mEncryptedSharedPreferences.all.keys) {
                    if (!mKeysChanged.contains(key)) {
                        mEditor.remove(mEncryptedSharedPreferences.encryptKey(key))
                    }
                }
            }
        }

        fun putEncryptedObject(key: String?, value: ByteArray?) {
            var key = key
            mKeysChanged.add(key)
            if (key == null) {
                key = NULL_VALUE
            }
            try {
                val encryptedPair = mEncryptedSharedPreferences.encryptKeyValuePair(key, value)
                mEditor.putString(encryptedPair.first, encryptedPair.second)
            } catch (ex: GeneralSecurityException) {
                throw SecurityException("Could not encrypt data: " + ex.message, ex)
            }
        }

        fun notifyListeners() {
            for (listener in mEncryptedSharedPreferences.mListeners) {
                for (key in mKeysChanged) {
                    listener.onSharedPreferenceChanged(mEncryptedSharedPreferences, key)
                }
            }
        }
    }

    override fun getAll(): MutableMap<String?, in Any?> {
        val allEntries: MutableMap<String?, in Any?> = HashMap()
        for ((key) in mSharedPreferences.all.entries) {
            val decryptedKey = decryptKey(key)
            allEntries[decryptedKey] = getDecryptedObject(decryptedKey)
        }
        return allEntries
    }

    override fun getString(key: String?, defValue: String?): String? {
        val value = getDecryptedObject(key)
        return (if (value is String) value else defValue)
    }

    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? {

        val value = getDecryptedObject(key)
        val returnValues = if (value is Set<*>) {
            value as Set<String>
        } else {
            ArraySet()
        }
        return returnValues.ifEmpty { defValues }
    }

    override fun getInt(key: String?, defValue: Int): Int {
        val value = getDecryptedObject(key)
        return (if (value is Int) value else defValue)
    }

    override fun getLong(key: String?, defValue: Long): Long {
        val value = getDecryptedObject(key)
        return (if (value is Long) value else defValue)
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        val value = getDecryptedObject(key)
        return (if (value is Float) value else defValue)
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        val value = getDecryptedObject(key)
        return (if (value is Boolean) value else defValue)
    }

    override fun contains(key: String?): Boolean {
        val encryptedKey = encryptKey(key)
        return mSharedPreferences.contains(encryptedKey)
    }

    override fun edit(): SharedPreferences.Editor {
        return Editor(this, mSharedPreferences.edit())
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        mListeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        mListeners.remove(listener)
    }

    /**
     * Internal enum to set the type of encrypted data.
     */
    enum class EncryptedType(val id: Int) {
        STRING(0), STRING_SET(1), INT(2), LONG(3), FLOAT(4), BOOLEAN(5);

        companion object {
            fun fromId(id: Int): EncryptedType? {
                when (id) {
                    0 -> return STRING
                    1 -> return STRING_SET
                    2 -> return INT
                    3 -> return LONG
                    4 -> return FLOAT
                    5 -> return BOOLEAN
                }
                return null
            }
        }
    }

    private fun encryptKey(key: String?): String? {
        return encryptString(key ?: NULL_VALUE)
    }

    private fun decryptKey(encryptedKey: String?): String {
        var key = decryptString(encryptedKey)
        if (key == null) {
            key = NULL_VALUE
        }
        return key
    }

    @Throws(SecurityException::class)
    private fun getDecryptedObject(key: String?): Any? {
        var key = key
        if (key == null) {
            key = NULL_VALUE
        }

        try {
            val encryptedKey = encryptKey(key)

            val encryptedValue: String =
                mSharedPreferences.getString(encryptedKey, null) ?: return null

            val value: ByteArray = decrypt(encryptedValue) ?: return null

            val buffer = ByteBuffer.wrap(value)
            buffer.position(0)
            val typeId = buffer.getInt()
            val type = EncryptedType.fromId(
                typeId
            ) ?: throw SecurityException("Unknown type ID for encrypted pref value: $typeId")

            when (type) {
                EncryptedType.STRING -> {
                    val stringLength = buffer.getInt()
                    val stringSlice = buffer.slice()
                    buffer.limit(stringLength)

                    val stringValue = StandardCharsets.UTF_8.decode(stringSlice).toString()
                    if (stringValue == NULL_VALUE) {
                        return null
                    }

                    return stringValue
                }

                EncryptedType.INT -> return buffer.getInt()
                EncryptedType.LONG -> return buffer.getLong()
                EncryptedType.FLOAT -> return buffer.getFloat()
                EncryptedType.BOOLEAN -> return buffer.get() != 0.toByte()
                EncryptedType.STRING_SET -> {
                    val stringSet = ArraySet<String>()

                    while (buffer.hasRemaining()) {
                        val subStringLength = buffer.getInt()
                        val subStringSlice = buffer.slice()
                        subStringSlice.limit(subStringLength)
                        buffer.position(buffer.position() + subStringLength)
                        stringSet.add(StandardCharsets.UTF_8.decode(subStringSlice).toString())
                    }

                    if (stringSet.size == 1 && NULL_VALUE == stringSet.valueAt(0)) {
                        return null
                    }

                    return stringSet
                }

                else -> throw SecurityException("Unhandled type for encrypted pref value: $type")
            }
        } catch (ex: GeneralSecurityException) {
            throw SecurityException("Could not decrypt value. " + ex.message, ex)
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun encryptKeyValuePair(key: String?, value: ByteArray?): Pair<String, String> {
        val encryptedKey = encryptKey(key)
        val cipherText = encrypt(value)
        return Pair(encryptedKey, cipherText)
    }
}
