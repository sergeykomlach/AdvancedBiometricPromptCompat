package org.chickenhook.restrictionbypass

import android.os.Build
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

object RestrictionBypass {
    @Throws(
        NoSuchMethodException::class,
        InvocationTargetException::class,
        IllegalAccessException::class
    )
    fun getDeclaredMethod(clazz: Any?, name: String?, vararg args: Class<*>?): Method? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NativeReflectionBypass.getDeclaredMethod(clazz, name, arrayOf(*args))
        } else {
            val params = arrayOf<Class<*>>()
            val getDeclaredMethod = Class::class.java.getMethod(
                "getDeclaredMethod",
                String::class.java, params::class.java
            )
            getDeclaredMethod.invoke(clazz, name, args) as Method
        }
    }

    @Throws(
        NoSuchMethodException::class,
        InvocationTargetException::class,
        IllegalAccessException::class
    )
    fun getMethod(clazz: Any?, name: String?, vararg args: Class<*>?): Method? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NativeReflectionBypass.getMethod(clazz, name, arrayOf(*args))
        } else {
            val params = arrayOf<Class<*>>()
            val getDeclaredMethod = Class::class.java.getMethod(
                "getMethod",
                String::class.java, params::class.java
            )
            getDeclaredMethod.invoke(clazz, name, args) as Method
        }
    }

    @Throws(
        NoSuchMethodException::class,
        InvocationTargetException::class,
        IllegalAccessException::class
    )
    fun getDeclaredField(obj: Class<*>?, name: String?): Field? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NativeReflectionBypass.getDeclaredField(obj, name)
        } else {
            val getDeclaredField =
                Class::class.java.getMethod("getDeclaredField", String::class.java)
            getDeclaredField.invoke(obj, name) as Field
        }
    }
}