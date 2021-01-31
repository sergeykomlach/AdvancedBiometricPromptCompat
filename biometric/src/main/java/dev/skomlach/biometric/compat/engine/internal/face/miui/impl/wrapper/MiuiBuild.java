package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper;

public class MiuiBuild {
    public static boolean IS_INTERNATIONAL_BUILD;
    public static String DEVICE;
    private static Class<?> clazz;

    static {
        try {
            clazz = Class.forName("miui.os.Build");
        } catch (Throwable e) {
        }
        try {
            IS_INTERNATIONAL_BUILD = clazz.getField("IS_INTERNATIONAL_BUILD").getBoolean(null);
        } catch (Throwable e) {
            IS_INTERNATIONAL_BUILD = false;
        }
        try {
            DEVICE = (String) clazz.getField("DEVICE").get(null);
        } catch (Throwable e) {
            DEVICE = null;
        }
    }

    public static String getRegion() {
        try {
            return (String) clazz.getMethod("getRegion").invoke(null);
        } catch (Throwable e) {
            return null;
        }
    }
}
