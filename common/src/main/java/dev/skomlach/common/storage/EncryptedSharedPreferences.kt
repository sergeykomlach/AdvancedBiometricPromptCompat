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
import androidx.collection.ArraySet
import androidx.core.content.ContextCompat
import com.tozny.crypto.android.AesCbcWithIntegrity
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets.UTF_8

private class KeyNameCipher(
    private val aesKey32: ByteArray
) {

    private val aad = "FNv2".toByteArray(UTF_8)

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

class EncryptedSharedPreferences internal constructor(
    private val mSharedPreferences: SharedPreferences,
    private val storageIdentity: String,
    private val migrationFence: PreferenceMigrationFence,
    legacyEncryptionConfigs: List<() -> SharedPreferenceProvider.EncryptionConfig?>,
    private val encryptionConfig: () -> SharedPreferenceProvider.EncryptionConfig?
) : SharedPreferences {
    constructor(context: Context, sharedPrefFilename: String? = null) : this(
        if (sharedPrefFilename.isNullOrEmpty()) PreferenceManager.getDefaultSharedPreferences(context)
        else context.getSharedPreferences(sharedPrefFilename, Context.MODE_PRIVATE),
        fileIdentity(context, sharedPrefFilename),
        KeyStorePreferenceMigrationFence(fileIdentity(context, sharedPrefFilename)),
        listOf({ SharedPreferenceProvider.EncryptionConfig.existingLegacyDeviceIdInstance },
            { SharedPreferenceProvider.EncryptionConfig.existingLegacyFileInstance }),
        { SharedPreferenceProvider.EncryptionConfig.keystoreOnlyInstance }
    )

    private class StoreState {
        var failure: ProtectedStorageUnavailableException? = null
        var committed = false
    }

    companion object {
        private const val NULL_VALUE = "__NULL__"
        private const val HEADER = "__esp_v3_format__"
        private val states = ConcurrentHashMap<String, StoreState>()

        internal fun fileIdentity(context: Context, filename: String?): String {
            val directory = ContextCompat.getDataDir(context)
                ?: throw ProtectedStorageUnavailableException("Cannot identify protected preference file")
            return directory.canonicalPath + "/shared_prefs/" +
                (filename?.takeIf { it.isNotEmpty() } ?: "${context.packageName}_preferences") + ".xml"
        }
    }

    private val state = states.getOrPut(storageIdentity) { StoreState() }
    private val mListeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
    private data class DerivedConfig(val valueKeys: AesCbcWithIntegrity.SecretKeys, val names: KeyNameCipher)
    private fun derive(config: SharedPreferenceProvider.EncryptionConfig) = DerivedConfig(
        AesCbcWithIntegrity.generateKeyFromPassword(String(config.password.reversedArray()), config.salt.reversedArray()),
        KeyNameCipher((config.password + config.salt).copyOf(32).reversedArray()))
    private val primary by lazy { derive(encryptionConfig()
        ?: throw ProtectedStorageUnavailableException("Protected preference primary key is unavailable")) }
    private val cipher by lazy { BoundPreferenceCipher(primary.valueKeys.integrityKey, storageIdentity) }
    private val legacy = legacyEncryptionConfigs.map { provider -> lazy { provider()?.let(::derive) } }
    private val configs get() = sequence {
        yield(primary)
        legacy.forEach { config -> config.value?.let { yield(it) } }
    }

    private inline fun <T> locked(action: () -> T): T = synchronized(state) {
        state.failure?.let { throw it }
        try {
            // No backing-store access or legacy fallback before the primary key is available.
            primary
            action()
        } catch (error: ProtectedStorageUnavailableException) { throw error }
        catch (error: Exception) { throw ProtectedStorageUnavailableException("Cannot access protected preferences", error) }
    }

    private fun fenceState(): String? = if (state.committed) PreferenceMigrationFence.COMMITTED
        else migrationFence.state()

    private fun snapshotDigest(snapshot: Map<String, *>): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(BoundPreferenceCipher.encodeFields("ProtectedPreferenceSnapshot3", snapshot.size.toString()))
        snapshot.toSortedMap().forEach { (key, value) ->
            val fields = if (value is Set<*>) arrayOf(key, "set", *value.map { it as String }.sorted().toTypedArray())
                else arrayOf(key, value?.javaClass?.name, value?.toString())
            digest.update(ByteBuffer.allocate(4).putInt(fields.size).array())
            digest.update(BoundPreferenceCipher.encodeFields(*fields))
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun checkFormat(snapshot: Map<String, *>): Boolean {
        val fence = fenceState()
        if (snapshot.containsKey(HEADER)) {
            cipher.verifyHeader(snapshot[HEADER] as? String
                ?: throw ProtectedStorageUnavailableException("Invalid protected preference header"))
            if (fence != PreferenceMigrationFence.COMMITTED) {
                // Recover only a fully authenticated v3 snapshot after a process interruption.
                names(snapshot, true).forEach { (key, physical) ->
                    decodeObject(cipher.decryptValue(key, storedValue(snapshot, physical)))
                }
                if (fence == null) migrationFence.begin(snapshotDigest(snapshot))
                migrationFence.complete()
            }
            state.committed = true
            return true
        }
        if (fence == PreferenceMigrationFence.COMMITTED)
            throw ProtectedStorageUnavailableException("Protected preference downgrade rejected")
        if (fence != null && fence != snapshotDigest(snapshot))
            throw ProtectedStorageUnavailableException("Interrupted preference migration source changed")
        return false
    }

    private fun names(snapshot: Map<String, *>, versioned: Boolean): Map<String?, String> {
        val result = LinkedHashMap<String?, String>()
        for (physical in snapshot.keys) {
            if (physical == HEADER) continue
            val key = if (versioned) cipher.decryptName(physical) else {
                val decoded = if (physical.isEmpty()) "" else configs.firstNotNullOfOrNull {
                    try { it.names.decryptName(physical) } catch (_: Exception) { null }
                } ?: throw ProtectedStorageUnavailableException("Cannot decrypt legacy preference name")
                decoded.takeUnless { it == NULL_VALUE }
            }
            if (result.containsKey(key)) throw ProtectedStorageUnavailableException("Ambiguous protected preference name")
            result[key] = physical
        }
        return result
    }

    private fun storedValue(snapshot: Map<String, *>, physical: String): String = snapshot[physical] as? String
        ?: throw ProtectedStorageUnavailableException("Invalid protected preference value")

    private fun legacyValue(snapshot: Map<String, *>, physical: String): ByteArray {
        val value = storedValue(snapshot, physical)
        return configs.firstNotNullOfOrNull {
            try { AesCbcWithIntegrity.decrypt(AesCbcWithIntegrity.CipherTextIvMac(value), it.valueKeys) }
            catch (_: Exception) { null }
        }?.also(::decodeObject) ?: throw ProtectedStorageUnavailableException("Cannot decrypt legacy preference value")
    }

    /** One durable replacement, then an irreversible Keystore fence; no legacy plaintext escapes first. */
    private fun migrate(snapshot: Map<String, *>, updates: Map<String?, ByteArray?> = emptyMap(), clear: Boolean = false) {
        val values = LinkedHashMap<String?, ByteArray>()
        if (!clear) names(snapshot, false).forEach { (key, physical) ->
            if (!updates.containsKey(key)) values[key] = legacyValue(snapshot, physical)
        }
        updates.forEach { (key, bytes) -> if (bytes != null) values[key] = bytes }
        val replacement = values.entries.associate { cipher.encryptName(it.key) to cipher.encryptValue(it.key, it.value) } +
            (HEADER to cipher.header())
        val fence = fenceState()
        if (fence == null || (!clear && fence != PreferenceMigrationFence.COMMITTED))
            migrationFence.begin(snapshotDigest(snapshot))
        persist {
            clear()
            replacement.forEach { (key, value) -> putString(key, value) }
        }
        migrationFence.complete()
        state.committed = true
    }

    private fun persist(action: SharedPreferences.Editor.() -> Unit) {
        try {
            val editor = mSharedPreferences.edit()
            editor.action()
            if (!editor.commit()) throw ProtectedStorageUnavailableException("Protected preference commit failed")
        } catch (error: Exception) {
            val failure = ProtectedStorageUnavailableException("Protected preference persistence failed; restart to reload disk state", error)
            // A failed commit may already have modified SharedPreferences' memory map. Block every wrapper.
            state.failure = failure
            throw failure
        }
    }

    private fun getDecryptedObject(key: String?): Any? = locked {
        var snapshot = mSharedPreferences.all
        if (!checkFormat(snapshot)) {
            migrate(snapshot)
            snapshot = mSharedPreferences.all
        }
        val physical = names(snapshot, true)[key] ?: return@locked null
        decodeObject(cipher.decryptValue(key, storedValue(snapshot, physical)))
    }

    override fun getAll(): MutableMap<String?, in Any?> = locked {
        var snapshot = mSharedPreferences.all
        if (!checkFormat(snapshot)) { migrate(snapshot); snapshot = mSharedPreferences.all }
        names(snapshot, true).mapValuesTo(LinkedHashMap()) { (key, physical) ->
            decodeObject(cipher.decryptValue(key, storedValue(snapshot, physical)))
        }
    }

    /** Names remain available for explicit removal of corrupt values; no read-side migration here. */
    fun keySet(): Set<String?> = locked {
        val snapshot = mSharedPreferences.all
        names(snapshot, checkFormat(snapshot)).keys.toSet()
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

    override fun contains(key: String?): Boolean = keySet().contains(key)
    override fun edit(): SharedPreferences.Editor = locked { Editor() }
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        mListeners.add(listener)
    }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        mListeners.remove(listener)
    }

    private inner class Editor : SharedPreferences.Editor {
        private val updates = LinkedHashMap<String?, ByteArray?>()
        private var clearRequested = false
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

        override fun remove(key: String?): SharedPreferences.Editor = apply { updates[key] = null }
        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }
        private fun putEncryptedObject(key: String?, value: ByteArray?) { updates[key] = value }

        override fun commit(): Boolean {
            val changed = locked {
                val snapshot = mSharedPreferences.all
                // An explicit clear may recover a damaged header/name without trusting its values.
                val versioned = if (clearRequested) false else checkFormat(snapshot)
                if (!versioned) migrate(snapshot, updates, clearRequested)
                else {
                    val aliases = names(snapshot, true)
                    val puts = updates.filterValues { it != null }.map { (key, bytes) ->
                        (aliases[key] ?: cipher.encryptName(key)) to cipher.encryptValue(key, bytes!!)
                    }
                    persist {
                        updates.filterValues { it == null }.keys.forEach { key -> aliases[key]?.let(::remove) }
                        puts.forEach { (key, value) -> putString(key, value) }
                    }
                }
                val changed = updates.keys.toList()
                updates.clear()
                clearRequested = false
                changed
            }
            // No listener is called by migration or under the store lock.
            mListeners.forEach { listener -> changed.forEach { listener.onSharedPreferenceChanged(this@EncryptedSharedPreferences, it) } }
            return true
        }

        // Protected mutations must be durable before publishing state or notifying consumers.
        override fun apply() { commit() }
    }

    private fun decodeObject(value: ByteArray): Any? {
        val buffer = ByteBuffer.wrap(value)
        fun text(): String {
            val size = buffer.int
            require(size >= 0 && size <= buffer.remaining())
            val bytes = ByteArray(size).also(buffer::get)
            return Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        }
        val decoded: Any? = when (buffer.int) {
            0 -> text().takeUnless { it == NULL_VALUE }
            1 -> LinkedHashSet<String>().apply { while (buffer.hasRemaining()) add(text()) }
                .takeUnless { it.size == 1 && it.single() == NULL_VALUE }
            2 -> buffer.int
            3 -> buffer.long
            4 -> buffer.float
            5 -> buffer.get().also { require(it == 0.toByte() || it == 1.toByte()) } == 1.toByte()
            else -> throw ProtectedStorageUnavailableException("Unknown protected preference value type")
        }
        require(!buffer.hasRemaining()) { "Trailing protected preference data" }
        return decoded
    }

    private enum class EncryptedType(val id: Int) {
        STRING(0), STRING_SET(1), INT(2), LONG(3), FLOAT(4), BOOLEAN(5)
    }
}
