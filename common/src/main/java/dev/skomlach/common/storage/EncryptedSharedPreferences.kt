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
import android.util.Base64
import android.util.Pair
import androidx.collection.ArraySet
import com.tozny.crypto.android.AesCbcWithIntegrity
import dev.skomlach.common.logging.LogCat
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets.UTF_8

class KeyNameCipher(
    private val aesKey32: ByteArray,
    private val secureRandom: SecureRandom = SecureRandom()
) {

    private val aad = "FNv2".toByteArray(UTF_8)

    fun encryptName(realName: String): String {
        val nonce12 = ByteArray(12)
        secureRandom.nextBytes(nonce12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKey32, "AES"),
            GCMParameterSpec(128, nonce12)
        )
        cipher.updateAAD(aad)
        val ct = cipher.doFinal(realName.toByteArray(UTF_8))

        val packed = ByteArray(nonce12.size + ct.size)
        System.arraycopy(nonce12, 0, packed, 0, nonce12.size)
        System.arraycopy(ct, 0, packed, nonce12.size, ct.size)

        val b64 = Base64.encodeToString(
            packed,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "e2_$b64"
    }

    fun decryptName(encName: String): String? {
        if (!encName.startsWith("e2_")) return null
        val packed = Base64.decode(encName.substring(3), Base64.URL_SAFE)
        if (packed.size < 12 + 16) return null

        val nonce = packed.copyOfRange(0, 12)
        val ct = packed.copyOfRange(12, packed.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKey32, "AES"),
            GCMParameterSpec(128, nonce)
        )
        cipher.updateAAD(aad)

        val pt = cipher.doFinal(ct)
        return String(pt, UTF_8)
    }
}

class EncryptedSharedPreferences(
    private val context: Context,
    private val sharedPrefFilename: String? = null
) : SharedPreferences {
    companion object {
        private const val NULL_VALUE = "__NULL__"
    }

    private data class DerivedConfig(
        val valueKeys: AesCbcWithIntegrity.SecretKeys,
        val fileNameCipher: KeyNameCipher
    )

    private val primaryConfig by lazy {
        deriveConfig(SharedPreferenceProvider.EncryptionConfig.instance)
    }
    private val legacyConfig by lazy {
        deriveConfig(SharedPreferenceProvider.EncryptionConfig.legacyInstance)
    }

    private fun deriveConfig(config: SharedPreferenceProvider.EncryptionConfig): DerivedConfig {
        return DerivedConfig(
            valueKeys = AesCbcWithIntegrity.generateKeyFromPassword(
                String(config.password.reversedArray()),
                config.salt.reversedArray()
            ),
            fileNameCipher = KeyNameCipher(
                (config.password + config.salt).copyOf(32).reversedArray()
            )
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
        return try {
            primaryConfig.fileNameCipher.decryptName(ciphertext)
                ?: legacyConfig.fileNameCipher.decryptName(ciphertext)
        } catch (e: Throwable) {
            null
        }
    }

    private fun encryptString(cleartext: String?): String? {
        if (cleartext.isNullOrEmpty()) {
            return cleartext
        }
        return primaryConfig.fileNameCipher.encryptName(cleartext)
    }

    private fun decrypt(ciphertext: String?): ByteArray? {
        if (ciphertext.isNullOrEmpty()) return ciphertext?.toByteArray(UTF_8)
        return decryptWith(primaryConfig.valueKeys, ciphertext)
            ?: decryptWith(legacyConfig.valueKeys, ciphertext)
    }

    private fun decryptWith(
        keys: AesCbcWithIntegrity.SecretKeys,
        ciphertext: String
    ): ByteArray? {
        return try {
            val cipherTextIvMac = AesCbcWithIntegrity.CipherTextIvMac(ciphertext)
            AesCbcWithIntegrity.decrypt(cipherTextIvMac, keys)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: UnsupportedEncodingException) {
            null
        }
    }

    private fun encrypt(cleartext: ByteArray?): String? {
        if (cleartext == null || cleartext.isEmpty()) {
            return String(cleartext ?: return null, UTF_8)
        }
        return try {
            AesCbcWithIntegrity.encrypt(cleartext, primaryConfig.valueKeys).toString()
        } catch (e: GeneralSecurityException) {
            LogCat.logException(e)
            null
        } catch (e: UnsupportedEncodingException) {
            LogCat.logException(e)
            null
        }
    }

    private inner class Editor(
        private val mEncryptedSharedPreferences: EncryptedSharedPreferences,
        editor: SharedPreferences.Editor
    ) : SharedPreferences.Editor {
        private val mEditor: SharedPreferences.Editor = editor
        private val mKeysChanged: MutableList<String?> = CopyOnWriteArrayList()
        private val mClearRequested = AtomicBoolean(false)

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            var outValue = value
            if (outValue == null) {
                outValue = NULL_VALUE
            }
            val stringBytes = outValue.toByteArray(StandardCharsets.UTF_8)
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
            var localValues = values
            if (localValues == null) {
                localValues = ArraySet()
                localValues.add(NULL_VALUE)
            }
            val byteValues: MutableList<ByteArray> = ArrayList(localValues.size)
            var totalBytes = localValues.size * Integer.BYTES
            for (strValue in localValues) {
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
            val encryptedKey = mEncryptedSharedPreferences.findEncryptedKey(key)
                ?: mEncryptedSharedPreferences.encryptKey(key)
            mEditor.remove(encryptedKey)
            mKeysChanged.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            mClearRequested.set(true)
            return this
        }

        override fun commit(): Boolean {
            clearKeysIfNeeded()
            val result = mEditor.commit()
            notifyListeners()
            return result
        }

        override fun apply() {
            clearKeysIfNeeded()
            mEditor.apply()
            notifyListeners()
        }

        private fun clearKeysIfNeeded() {
            if (mClearRequested.getAndSet(false)) {
                for ((key) in mEncryptedSharedPreferences.mSharedPreferences.all.entries) {
                    if (!mKeysChanged.contains(key)) {
                        mEditor.remove(key)
                    }
                }
            }
        }

        private fun notifyListeners() {
            for (listener in mEncryptedSharedPreferences.mListeners) {
                for (key in mKeysChanged) {
                    listener.onSharedPreferenceChanged(mEncryptedSharedPreferences, key)
                }
            }
        }

        private fun putEncryptedObject(key: String?, value: ByteArray?) {
            try {
                val encryptedPair = mEncryptedSharedPreferences.encryptKeyValuePair(key, value)
                mEditor.putString(encryptedPair.first, encryptedPair.second)
                mKeysChanged.add(key)
            } catch (e: GeneralSecurityException) {
                throw SecurityException("Could not encrypt data: ${e.message}", e)
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
            @Suppress("UNCHECKED_CAST")
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
        val encryptedKey = findEncryptedKey(key) ?: return false
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
                return when (id) {
                    0 -> STRING
                    1 -> STRING_SET
                    2 -> INT
                    3 -> LONG
                    4 -> FLOAT
                    5 -> BOOLEAN
                    else -> null
                }
            }
        }
    }

    private fun encryptKey(key: String?): String? {
        return encryptString(key ?: NULL_VALUE)
    }

    private fun decryptKey(encryptedKey: String?): String {
        var key = decryptString(encryptedKey)
        if (key == null) {
            key = encryptedKey ?: NULL_VALUE
        }
        return if (key == NULL_VALUE) NULL_VALUE else key
    }

    internal fun findEncryptedKey(key: String?): String? {
        val normalizedKey = key ?: NULL_VALUE
        val direct = encryptString(normalizedKey)
        if (direct != null && mSharedPreferences.contains(direct)) {
            return direct
        }
        for (storedKey in mSharedPreferences.all.keys) {
            if (decryptString(storedKey) == normalizedKey) {
                return storedKey
            }
        }
        return null
    }

    @Throws(SecurityException::class)
    private fun getDecryptedObject(key: String?): Any? {
        var localKey = key
        if (localKey == null) {
            localKey = NULL_VALUE
        }

        try {
            val encryptedKey = findEncryptedKey(localKey) ?: return null
            val encryptedValue: String =
                mSharedPreferences.getString(encryptedKey, null) ?: return null

            val value: ByteArray = decrypt(encryptedValue) ?: return null

            val buffer = ByteBuffer.wrap(value)
            buffer.position(0)
            val typeId = buffer.getInt()
            val type = EncryptedType.fromId(typeId)
                ?: throw SecurityException("Unknown type ID for encrypted pref value: $typeId")

            when (type) {
                EncryptedType.STRING -> {
                    val stringLength = buffer.getInt()
                    val stringSlice = buffer.slice()
                    stringSlice.limit(stringLength)
                    val stringValue = StandardCharsets.UTF_8.decode(stringSlice).toString()
                    return if (stringValue == NULL_VALUE) null else stringValue
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

                    return if (stringSet.size == 1 && NULL_VALUE == stringSet.valueAt(0)) {
                        null
                    } else {
                        stringSet
                    }
                }
            }
        } catch (ex: GeneralSecurityException) {
            throw SecurityException("Could not decrypt value. ${ex.message}", ex)
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun encryptKeyValuePair(key: String?, value: ByteArray?): Pair<String, String> {
        val encryptedKey = findEncryptedKey(key) ?: encryptKey(key)
        val cipherText = encrypt(value)
        return Pair(encryptedKey, cipherText)
    }
}
