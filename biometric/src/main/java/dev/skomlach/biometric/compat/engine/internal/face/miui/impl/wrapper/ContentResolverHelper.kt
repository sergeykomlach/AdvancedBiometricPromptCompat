package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper;

import android.database.ContentObserver;
import android.net.Uri;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

public class ContentResolverHelper {
    private static Class<?> clazz;

    static {
        try {
            clazz = Class.forName("android.content.ContentResolver");
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
    }

    public static void registerContentObserver(android.content.ContentResolver cr, Uri uri, boolean notifyForDescendents,
                                               ContentObserver observer, int userHandle) {
        try {
            clazz.getMethod("registerContentObserver", Uri.class, boolean.class, ContentObserver.class, int.class).
                    invoke(cr, uri, notifyForDescendents, observer, userHandle);
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
            try {
                cr.registerContentObserver(uri, notifyForDescendents, observer);
            } catch (Throwable e2) {
                BiometricLoggerImpl.e(e2);
            }
        }
    }
}
