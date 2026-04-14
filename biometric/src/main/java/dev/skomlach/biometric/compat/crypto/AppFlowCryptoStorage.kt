package dev.skomlach.biometric.compat.crypto

import android.util.Base64
import androidx.core.content.edit
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import java.security.SecureRandom

object AppFlowCryptoStorage {

    private const val PREFS_NAME = "app_flow_crypto_storage"
    private const val SALT_PREFIX = "salt."

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    fun getOrCreateSalt(keyName: String): ByteArray {
        val existing = prefs.getString(SALT_PREFIX + keyName, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }

        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        prefs.edit {
            putString(SALT_PREFIX + keyName, Base64.encodeToString(salt, Base64.NO_WRAP))
        }
        return salt
    }

    fun delete(keyName: String) {
        prefs.edit().remove(SALT_PREFIX + keyName).apply()
    }
}
