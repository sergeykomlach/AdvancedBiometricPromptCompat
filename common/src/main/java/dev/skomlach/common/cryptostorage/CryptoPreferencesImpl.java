package dev.skomlach.common.cryptostorage;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.securepreferences.SecurePreferences;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.skomlach.common.contextprovider.AndroidContext;
import dev.skomlach.common.logging.LogCat;

public class CryptoPreferencesImpl implements SharedPreferences {
    /*
    * For some reasons, AndroidX Security throws exception where not should.
    * This is a "soft" workaround for this bug.
    * Bug applicable at least for androidx.security:security-crypto:1.1.0-alpha02
    * 
    * I believe that issue related to the Android KeyStore internal code -
    *  perhaps some data remains in keystore after app delete or "Clear app data" call

       "Caused by java.lang.SecurityException: Could not decrypt value. decryption failed
       at androidx.security.crypto.EncryptedSharedPreferences.getDecryptedObject(EncryptedSharedPreferences.java:580)
       at androidx.security.crypto.EncryptedSharedPreferences.getLong(EncryptedSharedPreferences.java:437)"
    *
    */
    private SharedPreferences sharedPreferences;

    CryptoPreferencesImpl(@NonNull Context context, @NonNull String name) {
        Locale defaultLocale = AndroidContext.getLocale();
        setLocale(context, Locale.US);
        final SharedPreferences fallbackCheck = context.getSharedPreferences("FallbackCheck", Context.MODE_PRIVATE);
        boolean forceToFallback = fallbackCheck.getBoolean("forceToFallback", false);
        //AndroidX Security impl.
        //may produce exceptions on some devices (Huawei)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !forceToFallback) {
            try {
                final KeyGenParameterSpec keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC;
                final String masterKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec);
                sharedPreferences = EncryptedSharedPreferences
                        .create(
                                name,
                                masterKeyAlias,
                                context,
                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        );
                return;
            } catch (Exception e) {
                setLocale(context, defaultLocale);
                fallbackCheck.edit().putBoolean("forceToFallback", true).apply();
            }
        }

        //fallback
        sharedPreferences = new SecurePreferences(context, null, name, 5000);
        setLocale(context, defaultLocale);
    }

    //workaround for known date parsing issue in KeyPairGenerator
    private static void setLocale(Context context, Locale locale) {
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.locale = locale;
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
    }

    private boolean checkAndDeleteIfNeed(@Nullable String key, @NonNull Throwable e) {
        if (!e.toString().contains("Could not decrypt value")) {
            if (!TextUtils.isEmpty(key)) {
                LogCat.log("Remove broken value for key '" + key + "'");
                edit().remove(key).apply();
            } else {
                LogCat.log("Remove all broken values");
                edit().clear().apply();
            }
            return true;
        }
        return false;
    }

    private void checkException(@Nullable String key, @Nullable Throwable e) {
        if (e == null || checkAndDeleteIfNeed(key, e)) {
            return;
        }
        checkException(key, e.getCause());
    }

    @Nullable
    @Override
    public Map<String, ?> getAll() {
        try {
            return sharedPreferences.getAll();
        } catch (Throwable e) {
            checkException(null, e);
            LogCat.logException(e);
        }
        return null;
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        try {
            return sharedPreferences.getString(key, defValue);
        } catch (Throwable e) {
            checkException(key, e);
            LogCat.logException(e);
        }
        return defValue;
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        try {
            return sharedPreferences.getStringSet(key, defValues);
        } catch (Throwable e) {
            checkException(key, e);
            LogCat.logException(e);
        }
        return defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        try {
            return sharedPreferences.getInt(key, defValue);
        } catch (Throwable e) {
            checkException(key, e);
            LogCat.logException(e);
        }
        return defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        try {
            return sharedPreferences.getLong(key, defValue);
        } catch (Throwable e) {
            checkException(key, e);
            LogCat.logException(e);
        }
        return defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        try {
            return sharedPreferences.getFloat(key, defValue);
        } catch (Throwable e) {
            checkException(key, e);
            LogCat.logException(e);
        }
        return defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        try {
            return sharedPreferences.getBoolean(key, defValue);
        } catch (Throwable e) {
            checkException(key, e);
            LogCat.logException(e);
        }
        return defValue;
    }

    @Override
    public boolean contains(String key) {
        try {
            return sharedPreferences.contains(key);
        } catch (Throwable e) {
            checkException(key, e);
            LogCat.logException(e);
        }
        return false;
    }

    @Override
    public Editor edit() {
        return new CryptoEditor(sharedPreferences.edit());
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        try {
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener);
        } catch (Throwable e) {
            LogCat.logException(e);
        }
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        try {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener);
        } catch (Throwable e) {
            LogCat.logException(e);
        }
    }

    private static class CryptoEditor implements Editor {
        private final Editor editor;

        CryptoEditor(Editor editor) {
            this.editor = editor;
        }

        @Override
        public Editor putString(String key, @Nullable String value) {
            try {
                return new CryptoEditor(editor.putString(key, value));
            } catch (Throwable e) {
                LogCat.logException(e);
                return editor;
            }
        }

        @Override
        public Editor putStringSet(String key, @Nullable Set<String> values) {
            try {
                return new CryptoEditor(editor.putStringSet(key, values));
            } catch (Throwable e) {
                LogCat.logException(e);
                return editor;
            }
        }

        @Override
        public Editor putInt(String key, int value) {
            try {
                return new CryptoEditor(editor.putInt(key, value));
            } catch (Throwable e) {
                LogCat.logException(e);
                return editor;
            }
        }

        @Override
        public Editor putLong(String key, long value) {
            try {
                return new CryptoEditor(editor.putLong(key, value));
            } catch (Throwable e) {
                LogCat.logException(e);
                return editor;
            }
        }

        @Override
        public Editor putFloat(String key, float value) {
            try {
                return new CryptoEditor(editor.putFloat(key, value));
            } catch (Throwable e) {
                LogCat.logException(e);
                return editor;
            }
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            try {
                return new CryptoEditor(editor.putBoolean(key, value));
            } catch (Throwable e) {
                LogCat.logException(e);
                return editor;
            }
        }

        @Override
        public Editor remove(String key) {
            try {
                return new CryptoEditor(editor.remove(key));
            } catch (Throwable e) {
                LogCat.logException(e);
                return editor;
            }
        }

        @Override
        public Editor clear() {
            try {
                return new CryptoEditor(editor.clear());
            } catch (Throwable e) {
                LogCat.logException(e);
                return editor;
            }
        }

        @Override
        public boolean commit() {
            try {
                return editor.commit();
            } catch (Throwable e) {
                LogCat.logException(e);
                return false;
            }
        }

        @Override
        public void apply() {
            try {
                editor.apply();
            } catch (Throwable e) {
                LogCat.logException(e);
            }
        }
    }
}
