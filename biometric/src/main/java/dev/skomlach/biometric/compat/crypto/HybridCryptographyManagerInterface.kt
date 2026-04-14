package dev.skomlach.biometric.compat.crypto

import android.os.Build
import javax.crypto.Cipher

class HybridCryptographyManagerInterface : CryptographyManagerInterface {

    private val biometricDelegate: CryptographyManagerInterface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CryptographyManagerInterfaceMarshmallowImpl()
        } else {
            CryptographyManagerInterfaceKitkatImpl()
        }

    private val appFlowDelegate: CryptographyManagerInterface =
        AppFlowCryptographyManagerInterface()

    override val version: String
        get() = "hybrid-v2"

    override fun getInitializedCipherForEncryption(
        keyName: String,
        isUserAuthRequired: Boolean
    ): Cipher {
        return delegateFor(keyName).getInitializedCipherForEncryption(keyName, isUserAuthRequired)
    }

    override fun getInitializedCipherForDecryption(
        keyName: String,
        isUserAuthRequired: Boolean,
        initializationVector: ByteArray?
    ): Cipher {
        return delegateFor(keyName).getInitializedCipherForDecryption(
            keyName,
            isUserAuthRequired,
            initializationVector
        )
    }

    override fun deleteKey(keyName: String) {
        runCatching { biometricDelegate.deleteKey(keyName) }
        runCatching { appFlowDelegate.deleteKey(keyName) }
        AppFlowCryptoRegistry.clear(keyName)
    }

    private fun delegateFor(keyName: String): CryptographyManagerInterface {
        return when (AppFlowCryptoRegistry.getAccessType(keyName)) {
            CryptoAccessType.BIOMETRIC -> biometricDelegate
            CryptoAccessType.APP_FLOW -> appFlowDelegate
        }
    }
}
