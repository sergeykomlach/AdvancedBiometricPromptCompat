package org.chickenhook.restrictionbypass

import java.lang.reflect.Field
import java.lang.reflect.Method

internal object NativeReflectionBypass {
    external fun getDeclaredMethod(
        recv: Any?,
        name: String?,
        parameterTypes: Array<Class<*>?>?
    ): Method?

    external fun getMethod(recv: Any?, name: String?, parameterTypes: Array<Class<*>?>?): Method?
    external fun getDeclaredField(recv: Any?, name: String?): Field?

    init {
        System.loadLibrary("nrb")
    }
}