package dev.skomlach.biometric.compat.crypto

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.AlgorithmParameters
import java.security.Key
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherSpi
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.ShortBufferException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * New records use a versioned, 20-byte GCM IV (4-byte format + 128 random bits).
 * This is the actual IV passed to GCM, not a replacement for Cipher.iv. Callers must
 * persist the complete IV. Historical records used the provider's 12-byte IV.
 * Only those historical records may try the old key, and only after GCM rejects
 * the protected key's authentication tag. New records never use that fallback.
 */
internal object AppFlowCipherFactory {
    private val format = byteArrayOf(0x41, 0x46, 0x04, 0x01)

    fun isVersionedIv(iv: ByteArray): Boolean =
        iv.size == 20 && format.indices.all { iv[it] == format[it] }

    fun isLegacyIv(iv: ByteArray): Boolean {
        if (iv.size == 12) return true
        require(isVersionedIv(iv)) {
            "Unsupported app-flow IV format"
        }
        return false
    }

    fun encrypt(secret: CharArray, salt: ByteArray): Cipher {
        val iv = ByteArray(20).also { SecureRandom().nextBytes(it) }
        format.copyInto(iv)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(secret, salt), GCMParameterSpec(128, iv))
        }
    }

    fun decrypt(secret: CharArray, salt: ByteArray, iv: ByteArray, legacySecret: CharArray? = null): Cipher {
        val legacyFormat = isLegacyIv(iv)
        val key = deriveKey(secret, salt)
        val params = GCMParameterSpec(128, iv)
        if (!legacyFormat || legacySecret == null || secret.contentEquals(legacySecret)) {
            return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, params) }
        }
        return Cipher.getInstance("AES/GCM/NoPadding", LegacyReadProvider(deriveKey(legacySecret, salt))).apply {
            init(Cipher.DECRYPT_MODE, key, params)
        }
    }

    private fun deriveKey(secret: CharArray, salt: ByteArray): SecretKey {
        val factory = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        } catch (_: NoSuchAlgorithmException) {
            // Android 23–25 provide HmacSHA256 but may not expose this SecretKeyFactory.
            null
        }
        val encoded = if (factory == null) {
            deriveAppFlowKeyWithHmac(secret, salt)
        } else {
            val spec = PBEKeySpec(secret, salt, APP_FLOW_PBKDF2_ITERATIONS, 256)
            try { factory.generateSecret(spec).encoded } finally { spec.clearPassword() }
        }
        return try { SecretKeySpec(encoded, "AES") } finally { encoded.fill(0) }
    }
}

private const val APP_FLOW_PBKDF2_ITERATIONS = 210_000

@JvmSynthetic
internal fun deriveAppFlowKeyWithHmac(secret: CharArray, salt: ByteArray): ByteArray {
    val utf8 = Charsets.UTF_8.encode(CharBuffer.wrap(secret))
    val password = ByteArray(utf8.remaining().coerceAtLeast(1))
    val mac = try {
        utf8.get(password, 0, utf8.remaining())
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password, "HmacSHA256")) }
    } finally {
        if (utf8.hasArray()) utf8.array().fill(0)
        password.fill(0)
    }
    // PBKDF2 needs one block for a 256-bit key with HMAC-SHA256.
    mac.update(salt)
    val block = mac.doFinal(byteArrayOf(0, 0, 0, 1))
    val key = block.copyOf()
    try {
        repeat(APP_FLOW_PBKDF2_ITERATIONS - 1) {
            mac.update(block)
            mac.doFinal(block, 0)
            for (i in key.indices) key[i] = (key[i].toInt() xor block[i].toInt()).toByte()
        }
        return key
    } catch (failure: Exception) {
        key.fill(0)
        throw failure
    } finally {
        block.fill(0)
    }
}

// Use the standard provider factory: Android's protected Cipher(spi, ...) constructor
// bypasses SPI initialization. This provider is local to this cipher, never registered globally.
private class LegacyReadProvider(legacyKey: SecretKey) :
    Provider("AppFlowLegacyRead", 1.0, "Authenticated legacy app-flow decryption") {
    init {
        putService(object : Service(this, "Cipher", "AES/GCM/NoPadding",
            LegacyReadCipherSpi::class.java.name, emptyList(), emptyMap()) {
            override fun newInstance(constructorParameter: Any?): Any = LegacyReadCipherSpi(legacyKey)
        })
    }
}

/** Buffers ciphertext so no unauthenticated plaintext escapes from either delegate. */
private class LegacyReadCipherSpi(private val legacyKey: SecretKey) : CipherSpi() {
    private val primary = Cipher.getInstance("AES/GCM/NoPadding")
    private val legacy = Cipher.getInstance("AES/GCM/NoPadding")
    private val input = ByteArrayOutputStream()
    private val aad = ByteArrayOutputStream()

    override fun engineSetMode(mode: String) { require(mode.equals("GCM", true)) }
    override fun engineSetPadding(padding: String) { require(padding.equals("NoPadding", true)) }
    override fun engineGetBlockSize() = primary.blockSize
    override fun engineGetOutputSize(inputLen: Int) = (input.size() + inputLen - 16).coerceAtLeast(0)
    override fun engineGetIV(): ByteArray = primary.iv
    override fun engineGetParameters(): AlgorithmParameters = primary.parameters

    override fun engineInit(opmode: Int, key: Key, random: SecureRandom?) {
        throw java.security.InvalidKeyException("GCM parameters required")
    }

    override fun engineInit(opmode: Int, key: Key, params: AlgorithmParameterSpec?, random: SecureRandom?) {
        require(opmode == Cipher.DECRYPT_MODE) { "Legacy compatibility cipher is decryption-only" }
        require(params is GCMParameterSpec && params.tLen == 128 && params.iv.size == 12)
        primary.init(opmode, key, params, random)
        legacy.init(opmode, legacyKey, params, random)
        input.reset()
        aad.reset()
    }

    override fun engineInit(opmode: Int, key: Key, params: AlgorithmParameters?, random: SecureRandom?) =
        engineInit(opmode, key, params?.getParameterSpec(GCMParameterSpec::class.java), random)

    override fun engineUpdate(bytes: ByteArray, offset: Int, length: Int): ByteArray {
        input.write(bytes, offset, length)
        return ByteArray(0)
    }

    override fun engineUpdate(bytes: ByteArray, offset: Int, length: Int, output: ByteArray, outputOffset: Int): Int {
        engineUpdate(bytes, offset, length)
        return 0
    }

    override fun engineUpdateAAD(bytes: ByteArray, offset: Int, length: Int) { aad.write(bytes, offset, length) }
    override fun engineUpdateAAD(src: ByteBuffer) {
        val bytes = ByteArray(src.remaining())
        src.get(bytes)
        aad.write(bytes)
    }

    override fun engineDoFinal(bytes: ByteArray?, offset: Int, length: Int): ByteArray {
        if (bytes != null) input.write(bytes, offset, length)
        val ciphertext = input.toByteArray()
        val associatedData = aad.toByteArray()
        try {
            primary.updateAAD(associatedData)
            return try {
                primary.doFinal(ciphertext)
            } catch (invalidTag: AEADBadTagException) {
                legacy.updateAAD(associatedData)
                legacy.doFinal(ciphertext)
            }
        } finally {
            input.reset()
            aad.reset()
            ciphertext.fill(0)
            associatedData.fill(0)
        }
    }

    override fun engineDoFinal(bytes: ByteArray?, offset: Int, length: Int, output: ByteArray, outputOffset: Int): Int {
        if (outputOffset < 0 || output.size - outputOffset < engineGetOutputSize(length)) throw ShortBufferException()
        val plaintext = engineDoFinal(bytes, offset, length)
        return try {
            plaintext.copyInto(output, outputOffset)
            plaintext.size
        } finally { plaintext.fill(0) }
    }
}
