package dev.skomlach.biometric.compat.crypto

import androidx.core.content.edit
import dev.skomlach.common.contextprovider.AndroidContext.appContext

object AppFlowCryptoRegistry {
    private const val PREFS_NAME = "app_flow_crypto_registry"
    private const val PREFIX = "access_type."

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    fun setAccessType(keyName: String, type: CryptoAccessType) {
        prefs.edit { putString(PREFIX + keyName, type.name) }
    }

    fun getAccessType(keyName: String): CryptoAccessType {
        val raw = prefs.getString(PREFIX + keyName, null) ?: return CryptoAccessType.BIOMETRIC
        return runCatching { CryptoAccessType.valueOf(raw) }.getOrDefault(CryptoAccessType.BIOMETRIC)
    }

    fun clear(keyName: String) {
        prefs.edit { remove(PREFIX + keyName) }
    }
}
