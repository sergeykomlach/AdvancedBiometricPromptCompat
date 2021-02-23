package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

object FeatureParser {
    private var clazz: Class<*>? = null

    init {
        try {
            clazz = Class.forName("miui.util.FeatureParser")
        } catch (e: Throwable) {
            e(e)
        }
    }

    fun getStringArray(s: String?): Array<String>? {
        return try {
            clazz?.getMethod("getStringArray", String::class.java)?.invoke(null, s) as Array<String>
        } catch (e: Throwable) {
            e(e)
            null
        }
    }

    fun getBoolean(s: String?, def: Boolean): Boolean {
        return try {
            clazz?.getMethod("getBoolean", Boolean::class.javaPrimitiveType)
                ?.invoke(null, s) as Boolean
        } catch (e: Throwable) {
            e(e)
            def
        }
    }
}