package dev.skomlach.common.cryptostorage

import android.content.SharedPreferences

interface CryptoPreferencesProvider {
    fun getCryptoPreferences(name: String): SharedPreferences
}