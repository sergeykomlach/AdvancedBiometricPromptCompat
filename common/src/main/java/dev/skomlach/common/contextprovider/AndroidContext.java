package dev.skomlach.common.contextprovider;

import android.app.Application;
import android.content.Context;

import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

import java.io.IOException;
import java.util.Locale;

public class AndroidContext {
    private static Application application;

    public static Application getAppContext() {
        if (application != null) {
            fixDirAccess(application);
            return application;
        }
        try {
            application = (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null, (Object[]) null);
        } catch (Throwable ignored) {
            try {
                application = (Application) Class.forName("android.app.AppGlobals")
                        .getMethod("getInitialApplication").invoke(null, (Object[]) null);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        fixDirAccess(application);
        return application;
    }

    //Solution from
    //https://github.com/google/google-authenticator-android/
    private static void fixDirAccess(Context context) {
        // Try to restrict data dir file permissions to owner (this app's UID) only. This mitigates the
        // security vulnerability where SQLite database transaction journals are world-readable.
        // See CVE-2011-3901 advisory for more information.
        // NOTE: This also prevents all files in the data dir from being world-accessible, which is fine
        // because this application does not need world-accessible files.
        try {
            restrictAccessToOwnerOnly(context.getApplicationInfo().dataDir);
        } catch (Throwable e) {
            // Ignore this exception and don't log anything to avoid attracting attention to this fix
        }
    }

    /**
     * Restricts the file permissions of the provided path so that only the owner (UID)
     * can access it.
     */

    private static void restrictAccessToOwnerOnly(String path) throws IOException {
        // IMPLEMENTATION NOTE: The code below simply invokes the hidden API
        // android.os.FileUtils.setPermissions(path, 0700, -1, -1) via Reflection.

        int errorCode;
        try {
            errorCode = (Integer) Class.forName("android.os.FileUtils")
                    .getMethod("setPermissions", String.class, int.class, int.class, int.class)
                    .invoke(null, path, 0700, -1, -1);
        } catch (Exception e) {
            // Can't chain exception because IOException doesn't have the right constructor on Froyo
            // and below
            throw new IOException("Failed to set permissions: " + e);
        }
        if (errorCode != 0) {
            throw new IOException("setPermissions failed with error code " + errorCode);
        }
    }

    public static Locale getLocale() {
        LocaleListCompat listCompat = ConfigurationCompat.getLocales(getAppContext().getResources().getConfiguration());
        Locale l = (!listCompat.isEmpty() ? listCompat.get(0) : Locale.getDefault());
        if (l == null) {
            l = Locale.getDefault();
        }
        return l;
    }
}
