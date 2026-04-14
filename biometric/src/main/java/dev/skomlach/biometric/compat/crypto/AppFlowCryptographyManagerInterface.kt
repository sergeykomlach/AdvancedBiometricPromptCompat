package dev.skomlach.biometric.compat.crypto

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class AppFlowCryptographyManagerInterface : CryptographyManagerInterface {

    override val version: String
        get() = "app-flow-v2"

    override fun getInitializedCipherForEncryption(
        keyName: String,
        isUserAuthRequired: Boolean
    ): Cipher {
        try {
            val secret = AppFlowSessionStore.consumeSecretOrNull(keyName)
                ?: throw IllegalStateException("App-flow session is not unlocked for key: $keyName")
            try {
                val salt = AppFlowCryptoStorage.getOrCreateSalt(keyName)
                val aesKey = deriveAesKey(secret, salt)
                val cipher = getCipher()
                cipher.init(Cipher.ENCRYPT_MODE, aesKey)
                return cipher
            } finally {
                secret.fill('\u0000')
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e, "AppFlow encryption init failed. KeyName=$keyName")
            throw e
        }
    }

    override fun getInitializedCipherForDecryption(
        keyName: String,
        isUserAuthRequired: Boolean,
        initializationVector: ByteArray?
    ): Cipher {
        try {
            val iv = initializationVector
                ?: throw IllegalArgumentException("Initialization vector is required for decryption")
            val secret = AppFlowSessionStore.consumeSecretOrNull(keyName)
                ?: throw IllegalStateException("App-flow session is not unlocked for key: $keyName")
            try {
                val salt = AppFlowCryptoStorage.getOrCreateSalt(keyName)
                val aesKey = deriveAesKey(secret, salt)
                val cipher = getCipher()
                cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
                return cipher
            } finally {
                secret.fill('\u0000')
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e, "AppFlow decryption init failed. KeyName=$keyName")
            throw e
        }
    }

    override fun deleteKey(keyName: String) {
        AppFlowSessionStore.close(keyName)
        AppFlowCryptoStorage.delete(keyName)
    }

    private fun getCipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    private fun deriveAesKey(secret: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(secret, salt, 210_000, 256)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val encoded = factory.generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
