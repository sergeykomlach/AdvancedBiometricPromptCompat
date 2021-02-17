package dev.skomlach.common.cryptostorage

import android.content.SharedPreferences
import dev.skomlach.common.contextprovider.AndroidContext.appContext

object SharedPreferenceProvider {
    private var dependencies: CryptoPreferencesProvider? = null

    @JvmStatic
    @Synchronized
    fun getCryptoPreferences(name: String): SharedPreferences {
        if (dependencies == null) {
            dependencies = EncryptedPreferencesProvider(appContext)
        }
        return (dependencies as CryptoPreferencesProvider).getCryptoPreferences(name)
    }
}