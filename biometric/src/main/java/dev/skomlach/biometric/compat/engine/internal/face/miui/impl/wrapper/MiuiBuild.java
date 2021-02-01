package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

public class MiuiBuild {
    public static boolean IS_INTERNATIONAL_BUILD;
    public static String DEVICE;
    private static Class<?> clazz;

    static {
        try {
            clazz = Class.forName("miui.os.Build");
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
        try {
            IS_INTERNATIONAL_BUILD = clazz.getField("IS_INTERNATIONAL_BUILD").getBoolean(null);
            DEVICE = (String) clazz.getField("DEVICE").get(null);
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
    }

    public static String getRegion() {
        try {
            return (String) clazz.getMethod("getRegion").invoke(null);
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
            return null;
        }
    }
}
