package dev.skomlach.biometric.compat.crypto

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.ShortBufferException
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class AppFlowCipherMigrationTest {
    private val name = "BiometricModule42"
    private val legacy = name.reversed().toCharArray()
    private val salt = ByteArray(32) { (it + 1).toByte() }
    private val plaintext = "retained app-flow data".toByteArray()

    private fun historical(secret: CharArray, aad: ByteArray = byteArrayOf()): Pair<ByteArray, ByteArray> {
        val spec = PBEKeySpec(secret, salt, 210_000, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        key.fill(0)
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext) to cipher.iv
    }

    @Test fun oldAndProtectedRecordsSurviveNewEncryptionAndStoreRecreation() {
        val storage = mutableMapOf<String, String>()
        fun store() = AppFlowSecretStore(storage::get) { key, value -> storage[key] = value; true }
        val old = List(2) { historical(legacy) }
        old.forEach { (data, iv) ->
            assertArrayEquals(plaintext, AppFlowCipherFactory.decrypt(store().getSecret(name, false), salt, iv, legacy).doFinal(data))
        }
        val protected = store().getSecret(name, true)
        val intermediate = historical(protected) // Existing v3 records also used a raw 12-byte IV.
        val cipher = AppFlowCipherFactory.encrypt(protected, salt)
        val newData = cipher.doFinal(plaintext)
        assertEquals(20, cipher.iv.size)
        assertTrue(AppFlowCipherFactory.isVersionedIv(cipher.iv))
        assertFalse(AppFlowCipherFactory.isVersionedIv(cipher.iv.copyOf().apply { this[0] = 0 }))
        assertFalse(AppFlowCipherFactory.isVersionedIv(intermediate.second))
        assertFalse(AppFlowCipherFactory.isLegacyIv(cipher.iv))
        val recreated = store().getSecret(name, false, allowLegacy = false)
        (old + intermediate).forEach { (data, iv) ->
            assertArrayEquals(plaintext, AppFlowCipherFactory.decrypt(recreated, salt, iv, legacy).doFinal(data))
        }
        assertArrayEquals(plaintext, AppFlowCipherFactory.decrypt(recreated, salt, cipher.iv).doFinal(newData))
        assertNotEquals(String(legacy), storage[name])
    }

    @Test fun newFormatCannotFallBackToPublicLegacyKey() {
        val cipher = AppFlowCipherFactory.encrypt(legacy, salt)
        val data = cipher.doFinal(plaintext)
        assertThrows(AEADBadTagException::class.java) {
            AppFlowCipherFactory.decrypt("wrong protected key".toCharArray(), salt, cipher.iv, legacy).doFinal(data)
        }
        assertThrows(IllegalStateException::class.java) {
            AppFlowSecretStore({ null }, { _, _ -> fail("must not write on decrypt"); false })
                .getSecret(name, false, allowLegacy = false)
        }
    }

    @Test fun legacyCompatibilityAuthenticatesAadAndBuffersUntilFinal() {
        val aad = "record-42".toByteArray()
        val (data, iv) = historical(legacy, aad)
        val cipher = AppFlowCipherFactory.decrypt("protected key".toCharArray(), salt, iv, legacy)
        cipher.updateAAD(ByteBuffer.wrap(aad))
        val pendingOutput = cipher.update(data, 0, 7)
        assertTrue(pendingOutput == null || pendingOutput.isEmpty())
        val output = ByteArray(plaintext.size + 2)
        assertThrows(ShortBufferException::class.java) {
            cipher.doFinal(data, 7, data.size - 7, ByteArray(1), 0)
        }
        val count = cipher.doFinal(data, 7, data.size - 7, output, 2)
        assertEquals(plaintext.size, count)
        assertArrayEquals(plaintext, output.copyOfRange(2, output.size))
        val wrongAad = AppFlowCipherFactory.decrypt("protected key".toCharArray(), salt, iv, legacy)
        wrongAad.updateAAD("record-43".toByteArray())
        assertThrows(AEADBadTagException::class.java) { wrongAad.doFinal(data) }
    }

    @Test fun tamperedLegacyDataNeverWritesPlaintext() {
        val (data, iv) = historical(legacy)
        data[data.lastIndex] = (data.last().toInt() xor 1).toByte()
        val output = ByteArray(plaintext.size) { 0x55 }
        val cipher = AppFlowCipherFactory.decrypt("protected key".toCharArray(), salt, iv, legacy)
        assertThrows(AEADBadTagException::class.java) { cipher.doFinal(data, 0, data.size, output, 0) }
        assertArrayEquals(ByteArray(output.size) { 0x55 }, output)
    }

    @Test fun explicitAppSecretAndConsumedSessionsCannotEnableLegacyFallback() {
        try {
            AppFlowSessionStore.unlock(name, "external secret".toCharArray())
            assertFalse(AppFlowSessionStore.consumeUnlockOrNull(name)!!.allowLegacyDecryption)
            assertNull(AppFlowSessionStore.consumeUnlockOrNull(name))
            AppFlowSessionStore.unlock(name, "protected key".toCharArray(), true)
            assertTrue(AppFlowSessionStore.consumeUnlockOrNull(name)!!.allowLegacyDecryption)
            assertNull(AppFlowSessionStore.consumeUnlockOrNull(name))
        } finally { AppFlowSessionStore.closeAll() }
        val (data, iv) = historical(legacy)
        assertThrows(AEADBadTagException::class.java) {
            AppFlowCipherFactory.decrypt("external secret".toCharArray(), salt, iv).doFinal(data)
        }
    }

    @Test fun unknownIvFormatsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { AppFlowCipherFactory.isLegacyIv(ByteArray(20)) }
        assertThrows(IllegalArgumentException::class.java) { AppFlowCipherFactory.isLegacyIv(ByteArray(11)) }
    }
}
