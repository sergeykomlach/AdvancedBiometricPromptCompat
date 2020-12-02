package dev.skomlach.common.cryptostorage;

import android.content.SharedPreferences;

public interface CryptoPreferencesProvider {
    SharedPreferences getCryptoPreferences(String name);
}
