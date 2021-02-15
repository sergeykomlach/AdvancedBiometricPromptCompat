package dev.skomlach.common.contextprovider

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.core.os.ConfigurationCompat
import java.io.IOException
import java.util.*

object AndroidContext {
    private var application: Application? = null
    @JvmStatic val appContext: Application
        get() {
            application?.let {
                fixDirAccess(it)
                return it
            }
            if (Looper.getMainLooper().thread !== Thread.currentThread()) throw IllegalThreadStateException(
                "Main thread required for correct init"
            )
            application = try {
                Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) as Application
            } catch (ignored: Throwable) {
                try {
                    Class.forName("android.app.AppGlobals")
                        .getMethod("getInitialApplication")
                        .invoke(null) as Application
                } catch (e: Throwable) {
                    throw RuntimeException(e)
                }
            }
            application?.let {
                fixDirAccess(it)
                return it
            }
            throw RuntimeException("Application is NULL")
        }

    //Solution from
    //https://github.com/google/google-authenticator-android/
    private fun fixDirAccess(context: Context) {
        // Try to restrict data dir file permissions to owner (this app's UID) only. This mitigates the
        // security vulnerability where SQLite database transaction journals are world-readable.
        // See CVE-2011-3901 advisory for more information.
        // NOTE: This also prevents all files in the data dir from being world-accessible, which is fine
        // because this application does not need world-accessible files.
        try {
            restrictAccessToOwnerOnly(context.applicationInfo.dataDir)
        } catch (e: Throwable) {
            // Ignore this exception and don't log anything to avoid attracting attention to this fix
        }
    }

    /**
     * Restricts the file permissions of the provided path so that only the owner (UID)
     * can access it.
     */
    @Throws(IOException::class)
    private fun restrictAccessToOwnerOnly(path: String) {
        // IMPLEMENTATION NOTE: The code below simply invokes the hidden API
        // android.os.FileUtils.setPermissions(path, 0700, -1, -1) via Reflection.
        val errorCode: Int = try {
            Class.forName("android.os.FileUtils")
                .getMethod(
                    "setPermissions",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                .invoke(null, path, 448, -1, -1) as Int
        } catch (e: Exception) {
            // Can't chain exception because IOException doesn't have the right constructor on Froyo
            // and below
            throw IOException("Failed to set permissions: $e")
        }
        if (errorCode != 0) {
            throw IOException("setPermissions failed with error code $errorCode")
        }
    }

    @JvmStatic val locale: Locale
        get() {
            val listCompat = ConfigurationCompat.getLocales(
                appContext.resources.configuration
            )
            var l = if (!listCompat.isEmpty) listCompat[0] else Locale.getDefault()
            if (l == null) {
                l = Locale.getDefault()
            }
            return l
        }
}