/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.biometric.compat.utils

import android.content.Context
import dalvik.system.DexFile
import java.io.File

/**
 * This class cannot be instantiated
 */
object SystemPropertiesProxy {
    /**
     * Get the value for the given key.
     *
     * @return an empty string if the key isn't found
     * @throws IllegalArgumentException if the key exceeds 32 characters
     */

    @Throws(IllegalArgumentException::class)
    fun get(context: Context, key: String): String? {
        var ret = ""
        try {
            val cl = context.classLoader
            val SystemProperties = cl.loadClass("android.os.SystemProperties")

//Parameters Types
            val paramTypes: Array<Class<*>?> = arrayOfNulls(1)
            paramTypes[0] = String::class.java
            val get = SystemProperties.getMethod("get", *paramTypes)

            //Parameters
            val params = arrayOfNulls<Any>(1)
            params[0] = key
            ret = get.invoke(SystemProperties, *params) as String
        } catch (iAE: IllegalArgumentException) {
            throw iAE
        } catch (e: Exception) {
            ret = ""
            //FIXME
        }
        return ret
    }

    /**
     * Get the value for the given key.
     *
     * @return if the key isn't found, return def if it isn't null, or an empty string otherwise
     * @throws IllegalArgumentException if the key exceeds 32 characters
     */

    @Throws(IllegalArgumentException::class)
    fun get(context: Context, key: String, def: String): String? {
        var ret = def
        try {
            val cl = context.classLoader
            val SystemProperties = cl.loadClass("android.os.SystemProperties")

//Parameters Types
            val paramTypes: Array<Class<*>?> = arrayOfNulls(2)
            paramTypes[0] = String::class.java
            paramTypes[1] = String::class.java
            val get = SystemProperties.getMethod("get", *paramTypes)

            //Parameters
            val params = arrayOfNulls<Any>(2)
            params[0] = key
            params[1] = def
            ret = get.invoke(SystemProperties, *params) as String
        } catch (iAE: IllegalArgumentException) {
            throw iAE
        } catch (e: Exception) {
            ret = def
            //FIXME
        }
        return ret
    }

    /**
     * Get the value for the given key, and return as an integer.
     *
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as an integer, or def if the key isn't found or
     * cannot be parsed
     * @throws IllegalArgumentException if the key exceeds 32 characters
     */

    @Throws(IllegalArgumentException::class)
    fun getInt(context: Context, key: String, def: Int): Int? {
        var ret = def
        try {
            val cl = context.classLoader
            val SystemProperties = cl.loadClass("android.os.SystemProperties")

//Parameters Types
            val paramTypes = arrayOfNulls<Class<*>?>(2)
            paramTypes[0] = String::class.java
            paramTypes[1] = Int::class.javaPrimitiveType
            val getInt = SystemProperties.getMethod("getInt", *paramTypes)

            //Parameters
            val params = arrayOfNulls<Any>(2)
            params[0] = key
            params[1] = def
            ret = getInt.invoke(SystemProperties, *params) as Int
        } catch (iAE: IllegalArgumentException) {
            throw iAE
        } catch (e: Exception) {
            ret = def
            //FIXME
        }
        return ret
    }

    /**
     * Get the value for the given key, and return as a long.
     *
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as a long, or def if the key isn't found or
     * cannot be parsed
     * @throws IllegalArgumentException if the key exceeds 32 characters
     */

    @Throws(IllegalArgumentException::class)
    fun getLong(context: Context, key: String, def: Long): Long? {
        var ret = def
        try {
            val cl = context.classLoader
            val SystemProperties = cl.loadClass("android.os.SystemProperties")

//Parameters Types
            val paramTypes = arrayOfNulls<Class<*>?>(2)
            paramTypes[0] = String::class.java
            paramTypes[1] = Long::class.javaPrimitiveType
            val getLong = SystemProperties.getMethod("getLong", *paramTypes)

            //Parameters
            val params = arrayOfNulls<Any>(2)
            params[0] = key
            params[1] = def
            ret = getLong.invoke(SystemProperties, *params) as Long
        } catch (iAE: IllegalArgumentException) {
            throw iAE
        } catch (e: Exception) {
            ret = def
            //FIXME
        }
        return ret
    }

    /**
     * Get the value for the given key, returned as a boolean.
     * Values 'n', 'no', '0', 'false' or 'off' are considered false.
     * Values 'y', 'yes', '1', 'true' or 'on' are considered true.
     * (case insensitive).
     * If the key does not exist, or has any other value, then the default
     * result is returned.
     *
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as a boolean, or def if the key isn't found or is
     * not able to be parsed as a boolean.
     * @throws IllegalArgumentException if the key exceeds 32 characters
     */

    @Throws(IllegalArgumentException::class)
    fun getBoolean(context: Context, key: String, def: Boolean): Boolean? {
        var ret = def
        try {
            val cl = context.classLoader
            val SystemProperties = cl.loadClass("android.os.SystemProperties")

//Parameters Types
            val paramTypes = arrayOfNulls<Class<*>?>(2)
            paramTypes[0] = String::class.java
            paramTypes[1] = Boolean::class.javaPrimitiveType
            val getBoolean = SystemProperties.getMethod("getBoolean", *paramTypes)

            //Parameters
            val params = arrayOfNulls<Any>(2)
            params[0] = key
            params[1] = def
            ret = getBoolean.invoke(SystemProperties, *params) as Boolean
        } catch (iAE: IllegalArgumentException) {
            throw iAE
        } catch (e: Exception) {
            ret = def
            //FIXME
        }
        return ret
    }

    /**
     * Set the value for the given key.
     *
     * @throws IllegalArgumentException if the key exceeds 32 characters
     * @throws IllegalArgumentException if the value exceeds 92 characters
     */

    @Throws(IllegalArgumentException::class)
    fun set(context: Context, key: String?, value: String?) {
        try {
            val df = DexFile(File("/system/app/Settings.apk"))
            val cl = context.classLoader
            val SystemProperties = Class.forName("android.os.SystemProperties")

//Parameters Types
            val paramTypes: Array<Class<*>?> = arrayOfNulls(2)
            paramTypes[0] = String::class.java
            paramTypes[1] = String::class.java
            val set = SystemProperties.getMethod("set", *paramTypes)

            //Parameters
            val params = arrayOfNulls<Any>(2)
            params[0] = key
            params[1] = value
            set.invoke(SystemProperties, *params)
        } catch (iAE: IllegalArgumentException) {
            throw iAE
        } catch (e: Exception) {
            //FIXME
        }
    }
}