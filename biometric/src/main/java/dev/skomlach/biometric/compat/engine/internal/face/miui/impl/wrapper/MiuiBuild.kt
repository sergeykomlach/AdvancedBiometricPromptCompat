package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

object MiuiBuild {
    var IS_INTERNATIONAL_BUILD = false
    var DEVICE: String? = null
    private var clazz: Class<*>? = null

    init {
        try {
            clazz = Class.forName("miui.os.Build")
        } catch (e: Throwable) {
            e(e)
        }
        try {
            IS_INTERNATIONAL_BUILD =
                clazz?.getField("IS_INTERNATIONAL_BUILD")?.getBoolean(null) ?: false
            DEVICE = clazz?.getField("DEVICE")?.get(null) as String?
        } catch (e: Throwable) {
            e(e)
        }
    }

    val region: String?
        get() = try {
            clazz?.getMethod("getRegion")?.invoke(null) as String?
        } catch (e: Throwable) {
            e(e)
            null
        }
}