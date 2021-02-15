package dev.skomlach.common.cryptostorage

import android.app.Application
import android.content.SharedPreferences
import java.util.*

class EncryptedPreferencesProvider(private val application: Application) :
    CryptoPreferencesProvider {
    override fun getCryptoPreferences(name: String): SharedPreferences {
        var preferences = cache[name]
        if (preferences == null) {
            preferences = CryptoPreferencesImpl(application, name)
            cache[name] = preferences
        }
        return preferences
    }

    companion object {
        private val cache: MutableMap<String, SharedPreferences> = HashMap()
    }
}