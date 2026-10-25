package dev.skomlach.common.storage

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Domain-separated AEAD. Version, physical store and nullable logical key are authenticated. */
internal class BoundPreferenceCipher(integrityKey: SecretKey, private val file: String) {
    private val key = SecretKeySpec(Mac.getInstance("HmacSHA256").run {
        init(integrityKey)
        doFinal("BiometricCompat/EncryptedPreferences/v3/AES-GCM".toByteArray(Charsets.UTF_8))
    }, "AES")
    private val random = SecureRandom()

    fun encryptName(name: String?): String = seal("e3_", "name", null, encodeFields(name))
    fun decryptName(value: String): String? {
        val buffer = ByteBuffer.wrap(open("e3_", "name", null, value))
        val size = buffer.int
        if (size == -1) {
            require(!buffer.hasRemaining())
            return null
        }
        require(size >= 0 && size == buffer.remaining())
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(buffer).toString()
    }

    fun encryptValue(name: String?, bytes: ByteArray) = seal("v3_", "value", name, bytes)
    fun decryptValue(name: String?, value: String) = open("v3_", "value", name, value)
    fun header() = seal("v3_", "format", null, byteArrayOf(3))
    fun verifyHeader(value: String) {
        require(open("v3_", "format", null, value).contentEquals(byteArrayOf(3)))
    }

    private fun seal(prefix: String, domain: String, name: String?, bytes: ByteArray): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(encodeFields("BiometricCompat", "3", domain, file, name))
        return prefix + Base64.encodeToString(iv + cipher.doFinal(bytes), Base64.NO_WRAP)
    }

    private fun open(prefix: String, domain: String, name: String?, value: String): ByteArray {
        require(value.startsWith(prefix)) { "Unknown protected preference version" }
        val packed = Base64.decode(value.substring(prefix.length), Base64.NO_WRAP)
        require(packed.size >= 28) { "Truncated protected preference" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        cipher.updateAAD(encodeFields("BiometricCompat", "3", domain, file, name))
        return cipher.doFinal(packed, 12, packed.size - 12)
    }

    companion object {
        /** Length delimiters and an explicit null distinguish empty, null and sentinel strings. */
        fun encodeFields(vararg fields: String?): ByteArray = ByteArrayOutputStream().use { out ->
            DataOutputStream(out).use { data ->
                fields.forEach { field ->
                    if (field == null) data.writeInt(-1) else {
                        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(field))
                        val bytes = ByteArray(encoded.remaining()).also(encoded::get)
                        data.writeInt(bytes.size)
                        data.write(bytes)
                    }
                }
            }
            out.toByteArray()
        }
    }
}
