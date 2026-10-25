package dev.skomlach.common.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.tozny.crypto.android.AesCbcWithIntegrity
import org.robolectric.RuntimeEnvironment
import java.nio.ByteBuffer
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class MemoryPreferenceFence : PreferenceMigrationFence {
    var value: String? = null
    var failComplete = false
    var begins = 0
    var completes = 0
    override fun state() = value
    override fun begin(sourceDigest: String) {
        check(value == null || value == sourceDigest)
        if (value == null) { value = sourceDigest; begins++ }
    }
    override fun complete() {
        if (failComplete) throw ProtectedStorageUnavailableException("Fixture fence unavailable")
        value = PreferenceMigrationFence.COMMITTED
        completes++
    }
}

internal class ProtectedPreferenceFixture {
    val id = "fixture-" + UUID.randomUUID()
    val raw: SharedPreferences = RuntimeEnvironment.getApplication().getSharedPreferences(id, Context.MODE_PRIVATE)
    val fence = MemoryPreferenceFence()
    val config = SharedPreferenceProvider.EncryptionConfig(ByteArray(32) { (it + 1).toByte() }, ByteArray(128) { it.toByte() })
    val legacyConfig = SharedPreferenceProvider.EncryptionConfig(ByteArray(32) { (it + 2).toByte() }, ByteArray(128) { (it + 3).toByte() })
    var legacyReads = 0
    fun open(backing: SharedPreferences = raw, identity: String = id) = EncryptedSharedPreferences(
        backing, identity, fence, listOf({ legacyReads++; legacyConfig }), { config })

    fun legacy(key: String?, bytes: ByteArray, useOldKey: Boolean = false) {
        val source = if (useOldKey) legacyConfig else config
        val encoded = if (key == "") "" else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12).also(java.security.SecureRandom()::nextBytes)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec((source.password + source.salt).copyOf(32).reversedArray(), "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD("FNv2".toByteArray())
            "e2_" + Base64.encodeToString(iv + cipher.doFinal((key ?: "__NULL__").toByteArray(Charsets.UTF_8)), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
        val keys = AesCbcWithIntegrity.generateKeyFromPassword(String(source.password.reversedArray()), source.salt.reversedArray())
        raw.edit().putString(encoded, AesCbcWithIntegrity.encrypt(bytes, keys).toString()).commit()
    }

    fun legacyString(key: String?, value: String, useOldKey: Boolean = false) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        legacy(key, ByteBuffer.allocate(8 + bytes.size).putInt(0).putInt(bytes.size).put(bytes).array(), useOldKey)
    }

    fun simulateProcessRestart() {
        @Suppress("UNCHECKED_CAST")
        val states = EncryptedSharedPreferences::class.java.getDeclaredField("states")
            .apply { isAccessible = true }.get(null) as MutableMap<String, *>
        states.remove(id)
    }
}
