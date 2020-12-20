package dev.skomlach.common.cryptoheap;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;

import androidx.security.crypto.MasterKeys;

import com.securepreferences.SecurePreferences;
import com.tozny.crypto.android.AesCbcWithIntegrity;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.security.KeyStore;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;

import dev.skomlach.common.contextprovider.AndroidContext;
import dev.skomlach.common.logging.LogCat;

public class CryptoWrapper {
    public static CryptoWrapper INSTANCE = new CryptoWrapper();
    private SecretKey secretKey;
    private String TRANSFORMATION;

    private CryptoWrapper() {
        Locale defaultLocale = AndroidContext.getLocale();
        setLocale(AndroidContext.getAppContext(), Locale.US);
        try {
            final SharedPreferences fallbackCheck = AndroidContext.getAppContext().getSharedPreferences( "FallbackCheck", Context.MODE_PRIVATE);
            boolean forceToFallback = fallbackCheck.getBoolean("forceToFallback", false);
            //AndroidX Security impl.
            //may produce exceptions on some devices (Huawei)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !forceToFallback) {
                final KeyGenParameterSpec keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC;
                final String masterKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec);
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                secretKey = (SecretKey) keyStore.getKey(masterKeyAlias, null);
                TRANSFORMATION = "AES/GCM/NoPadding";
            } else {
                //fallback for L and older
                SecurePreferences securePreferences = new SecurePreferences(AndroidContext.getAppContext(), 5000);
                Field field = SecurePreferences.class.getDeclaredField("keys");
                final boolean isAccessible = field.isAccessible();
                if (!isAccessible) {
                    field.setAccessible(true);
                }
                AesCbcWithIntegrity.SecretKeys keys = (AesCbcWithIntegrity.SecretKeys) field.get(securePreferences);
                if (!isAccessible) {
                    field.setAccessible(false);
                }
                secretKey = keys.getConfidentialityKey();
                TRANSFORMATION = "AES/CBC/PKCS5Padding";
            }
        } catch (Throwable e) {
            LogCat.logException(e);
            secretKey = null;
        }
        setLocale(AndroidContext.getAppContext(), defaultLocale);
    }

    //workaround for known date parsing issue in KeyPairGenerator
    private static void setLocale(Context context, Locale locale) {
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.locale = locale;
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
    }

    public final SealedObject wrapObject(Serializable object) {
        try {
            if (object == null)
                return null;
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return new SealedObject(object, cipher);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public final Object unwrapObject(SealedObject sealedObject) {
        try {
            if (sealedObject == null)
                return null;
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return sealedObject.getObject(cipher);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
