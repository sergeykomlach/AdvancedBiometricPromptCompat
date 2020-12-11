package dev.skomlach.biometric.compat.utils.hardware;

import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.RestrictTo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.KeyGenerator;

import dev.skomlach.biometric.compat.engine.BiometricAuthentication;
import dev.skomlach.biometric.compat.utils.LockType;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.contextprovider.AndroidContext;
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider;

//Set of tools that tried to behave like BiometricManager API from Android 10
@TargetApi(Build.VERSION_CODES.P)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class Android28Hardware implements HardwareInfo {
    private static final String TS_PREF = "timestamp_";
    private static final long timeout = TimeUnit.SECONDS.toMillis(31);
    private final SharedPreferences preferences;

    public Android28Hardware() {
        preferences = SharedPreferenceProvider.getCryptoPreferences("BiometricModules");
    }

    private static int tag() {
        return Integer.MAX_VALUE;
    }

    private ArrayList<String> biometricFeatures() {
        ArrayList<String> list = new ArrayList<>();
        try {
            Field[] fields = PackageManager.class.getFields();
            for (Field f : fields) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType().equals(String.class)) {
                    String name = (String) f.get(null);
                    if (name == null)
                        continue;

                    if (name.endsWith(".fingerprint")
                            || name.endsWith(".face")
                            || name.endsWith(".iris")
                            || name.endsWith(".biometric")

                    ) {
                        list.add(name);
                    }
                }
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }

        Collections.sort(list);
        return list;
    }

    @Override
    public boolean isHardwareAvailable() {
        if (BiometricAuthentication.isHardwareDetected())
            return true;

        ArrayList<String> list = biometricFeatures();
        PackageManager packageManager = AndroidContext.getAppContext().getPackageManager();
        for (String f : list) {
            if (packageManager != null && packageManager.hasSystemFeature(f)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isBiometricEnrolled() {
        KeyguardManager keyguardManager =
                (KeyguardManager) AndroidContext.getAppContext().getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager != null && keyguardManager.isDeviceSecure()) {

            if (BiometricAuthentication.hasEnrolled()
                    || LockType.isBiometricWeakEnabled(AndroidContext.getAppContext())) {
                return true;
            }

            //Fallback for some devices where previews methods failed

            //https://stackoverflow.com/a/53973970
            KeyStore keyStore = null;
            String name = UUID.randomUUID().toString();
            try {
                keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStore.getProvider());
                KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(name,
                        KeyProperties.PURPOSE_ENCRYPT |
                                KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .setUserAuthenticationRequired(true)
                        .setInvalidatedByBiometricEnrollment(true);
                keyGenerator.init(builder.build());//exception should be thrown here on "normal" devices if no enrolled biometric

//                keyGenerator.generateKey();
//
//                //Devices with a bug in Keystore
//                //https://issuetracker.google.com/issues/37127115
//                //https://stackoverflow.com/questions/42359337/android-key-invalidation-when-fingerprints-removed
//                try {
//                    SecretKey symKey = (SecretKey) keyStore.getKey(name, null);
//                    Cipher sym = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
//                            + KeyProperties.BLOCK_MODE_CBC + "/"
//                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);
//                    sym.init(Cipher.ENCRYPT_MODE, symKey);
//                    sym.doFinal(name.getBytes("UTF-8"));
//                } catch (Throwable e) {
//                    //at least one biometric enrolled
//                    return BiometricAuthentication.hasEnrolled();
//                }
            } catch (Throwable e) {
                Throwable cause = e.getCause();
                while (cause != null && !cause.equals(e)) {
                    e = cause;
                    cause = e.getCause();
                }
                if (e instanceof IllegalStateException) {
                    return false;
                }
            } finally {
                try {
                    if (keyStore != null)
                        keyStore.deleteEntry(name);
                } catch (Throwable ignore) {
                }
            }

            return true;
        }

        return false;
    }

    public void lockout() {
        if (!isLockedOut()) {
            preferences.edit().putLong(TS_PREF + tag(), System.currentTimeMillis()).apply();
        }
    }

    @Override
    public boolean isLockedOut() {
        long ts = preferences.getLong(TS_PREF + tag(), 0);

        if (ts > 0) {
            if (System.currentTimeMillis() - ts > timeout) {
                preferences.edit().putLong(TS_PREF + tag(), 0).apply();
                return false;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }
}
