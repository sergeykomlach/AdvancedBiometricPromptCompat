package dev.skomlach.common.cryptostorage;

import android.content.SharedPreferences;

import dev.skomlach.common.contextprovider.AndroidContext;

public class SharedPreferenceProvider {

    private static CryptoPreferencesProvider dependencies;

    public static SharedPreferences getCryptoPreferences(String name) {
        if (dependencies == null) {
            dependencies = new EncryptedPreferencesProvider(AndroidContext.getAppContext());
        }
        return dependencies.getCryptoPreferences(name);
    }
}
