package dev.skomlach.common.cryptostorage;

import android.app.Application;
import android.content.SharedPreferences;

public class EncryptedPreferencesProvider implements CryptoPreferencesProvider {
    private final Application application;

    public EncryptedPreferencesProvider(Application application) {
        this.application = application;
    }

    @Override
    public SharedPreferences getCryptoPreferences(String name) {
        return new CryptoPreferencesImpl(application, name);
    }
}
