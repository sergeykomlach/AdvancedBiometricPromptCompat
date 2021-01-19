package dev.skomlach.common.cryptostorage;

import android.app.Application;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

public class EncryptedPreferencesProvider implements CryptoPreferencesProvider {
    private static Map<String, SharedPreferences> cache = new HashMap<>();
    private final Application application;

    public EncryptedPreferencesProvider(Application application) {
        this.application = application;
    }

    @Override
    public SharedPreferences getCryptoPreferences(String name) {
        SharedPreferences preferences = cache.get(name);
        if (preferences == null) {
            preferences = new CryptoPreferencesImpl(application, name);
            cache.put(name, preferences);
        }

        return preferences;
    }
}
