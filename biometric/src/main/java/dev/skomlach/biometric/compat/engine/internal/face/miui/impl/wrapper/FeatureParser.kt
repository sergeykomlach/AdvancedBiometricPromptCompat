package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

public class FeatureParser {
    private static Class<?> clazz;

    static {
        try {
            clazz = Class.forName("miui.util.FeatureParser");
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
    }

    public static String[] getStringArray(String s) {
        try {
            return (String[]) clazz.getMethod("getStringArray", String.class).
                    invoke(null, s);
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
            return null;
        }
    }

    public static boolean getBoolean(String s, boolean def) {
        try {
            return (boolean) clazz.getMethod("getBoolean", boolean.class).
                    invoke(null, s);
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
            return def;
        }
    }
}
