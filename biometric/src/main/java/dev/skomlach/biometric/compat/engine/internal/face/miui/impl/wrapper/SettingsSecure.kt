package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper

import android.content.ContentResolver
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

object SettingsSecure {
    private var clazz: Class<*>? = null

    init {
        try {
            clazz = Class.forName("android.provider.Settings\$Secure")
        } catch (e: Throwable) {
            e(e)
        }
    }

    fun getIntForUser(cr: ContentResolver?, name: String?, def: Int, userHandle: Int): Int {
        return try {
            clazz?.getMethod(
                "getIntForUser",
                ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )?.invoke(null, cr, name, def, userHandle) as Int
        } catch (e: Throwable) {
            def
        }
    }

    fun getStringForUser(
        cr: ContentResolver?,
        name: String?,
        def: String,
        userHandle: Int
    ): String {
        return try {
            clazz?.getMethod(
                "getStringForUser",
                ContentResolver::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )?.invoke(null, cr, name, def, userHandle) as String
        } catch (e: Throwable) {
            def
        }
    }
}