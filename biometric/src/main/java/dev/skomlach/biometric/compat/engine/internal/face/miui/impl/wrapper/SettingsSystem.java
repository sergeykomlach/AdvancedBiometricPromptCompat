package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper;

import android.content.ContentResolver;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

public class SettingsSystem {
    private static Class<?> clazz;

    static {
        try {
            clazz = Class.forName("android.provider.Settings$System");
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
    }

    public static int getIntForUser(ContentResolver cr, String name, int def, int userHandle) {
        try {
            return (int) clazz.
                    getMethod("getIntForUser", ContentResolver.class, String.class, int.class, int.class).
                    invoke(null, cr, name, def, userHandle);
        } catch (Throwable e) {
            return def;
        }
    }

    public static String getStringForUser(ContentResolver cr, String name, String def, int userHandle) {
        try {
            return (String) clazz.
                    getMethod("getStringForUser", ContentResolver.class, String.class, String.class, int.class).
                    invoke(null, cr, name, def, userHandle);
        } catch (Throwable e) {
            return def;
        }
    }
}
