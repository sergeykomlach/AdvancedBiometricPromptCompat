package dev.skomlach.biometric.compat.crypto

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import javax.crypto.Cipher

class AppFlowCryptographyManagerInterface : CryptographyManagerInterface {

    override val version: String
        get() = "app-flow-v4"

    override fun getInitializedCipherForEncryption(
        keyName: String,
        isUserAuthRequired: Boolean
    ): Cipher {
        require(!isUserAuthRequired) { "App-flow keys cannot require biometric-bound cryptography" }
        try {
            val secret = AppFlowSessionStore.consumeSecretOrNull(keyName)
                ?: throw IllegalStateException("App-flow session is not unlocked for key: $keyName")
            try {
                val salt = AppFlowCryptoStorage.getOrCreateSalt(keyName)
                return AppFlowCipherFactory.encrypt(secret, salt)
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
        require(!isUserAuthRequired) { "App-flow keys cannot require biometric-bound cryptography" }
        try {
            val iv = initializationVector
                ?: throw IllegalArgumentException("Initialization vector is required for decryption")
            val secret = AppFlowSessionStore.consumeSecretOrNull(keyName)
                ?: throw IllegalStateException("App-flow session is not unlocked for key: $keyName")
            try {
                val salt = AppFlowCryptoStorage.requireSalt(keyName)
                return AppFlowCipherFactory.decrypt(secret, salt, iv)
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

}
