package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

object ContentResolverHelper {
    private var clazz: Class<*>? = null

    init {
        try {
            clazz = Class.forName("android.content.ContentResolver")
        } catch (e: Throwable) {
            e(e)
        }
    }

    fun registerContentObserver(
        cr: ContentResolver, uri: Uri, notifyForDescendents: Boolean,
        observer: ContentObserver, userHandle: Int
    ) {
        try {
            clazz?.getMethod(
                "registerContentObserver",
                Uri::class.java,
                Boolean::class.javaPrimitiveType,
                ContentObserver::class.java,
                Int::class.javaPrimitiveType
            )?.invoke(cr, uri, notifyForDescendents, observer, userHandle)
        } catch (e: Throwable) {
            e(e)
            try {
                cr.registerContentObserver(uri, notifyForDescendents, observer)
            } catch (e2: Throwable) {
                e(e2)
            }
        }
    }
}