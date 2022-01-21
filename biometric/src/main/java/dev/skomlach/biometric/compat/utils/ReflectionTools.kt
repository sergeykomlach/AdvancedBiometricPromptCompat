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
import android.content.pm.PackageManager
import androidx.core.os.BuildCompat
import dalvik.system.PathClassLoader
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import java.lang.reflect.Modifier
import java.util.*


object ReflectionTools {
    private val cache = WeakHashMap<String, PathClassLoader>()

    @Throws(Exception::class)
    fun getPathClassLoaderForPkg(pkg: String): PathClassLoader {
        var pathClassLoader = cache[pkg]
        if (pathClassLoader == null) {
            var apkName: String? = null
            try {
                apkName = appContext.packageManager.getApplicationInfo(
                    pkg, 0
                ).sourceDir
            } catch (e: Throwable) {
                if (BuildCompat.isAtLeastN()) apkName =
                    appContext.packageManager.getApplicationInfo(
                        pkg, PackageManager.MATCH_SYSTEM_ONLY
                    ).sourceDir
            }
            pathClassLoader = PathClassLoader(
                apkName,
                ClassLoader.getSystemClassLoader()
            )
            cache[pkg] = pathClassLoader
        }
        return pathClassLoader
    }


    @Throws(ClassNotFoundException::class)
    fun getClassFromPkg(pkg: String, cls: String): Class<*> {
        return try {
            Class.forName(cls, true, getPathClassLoaderForPkg(pkg))
        } catch (e: Throwable) {
            throw ClassNotFoundException("Class '$pkg/$cls' not found", e)
        }
    }

    fun checkBooleanMethodForSignature(
        clazz: Class<*>,
        managerObject: Any?,
        vararg keywords: String?
    ): Boolean {
        try {
            val allMethods = clazz.methods
            for (m in allMethods) {
                try {
                    val isReturnBoolean =
                        Boolean::class.javaPrimitiveType?.name == m.returnType.name || Boolean::class.java.name == m.returnType.name
                    var containsKeyword = false
                    val name = m.name
                    for (s in keywords) {
                        if (!s.isNullOrEmpty() && name.contains(s)) {
                            containsKeyword = true
                            break
                        }
                    }
                    if (Modifier.isPublic(m.modifiers) && isReturnBoolean && containsKeyword) {
                        d("Method: " + m.name)
                        if (m.parameterTypes.isEmpty()) try {
                            return if (Boolean::class.javaPrimitiveType?.name == m.returnType.name) {
                                m.invoke(managerObject) as Boolean
                            } else {
                                m.invoke(managerObject) as Boolean
                            }
                        } catch (e: Throwable) {
                            e(e)
                        }
                    }
                } catch (e: Throwable) {
                    e(e)
                }
            }
        } catch (e: Throwable) {
            e(e)
        }
        return false
    }

    fun checkIntMethodForSignature(clazz: Class<*>, managerObject: Any?, startWith: String): Int {
        try {
            val allMethods = clazz.methods
            for (m in allMethods) {
                try {
                    val isReturnInt =
                        Int::class.javaPrimitiveType?.name == m.returnType.name || Int::class.java.name == m.returnType.name
                    if (Modifier.isPublic(m.modifiers) && isReturnInt && m.name.startsWith(startWith)) {
                        d("Method: " + m.name)
                        if (m.parameterTypes.isEmpty()) try {
                            return if (Int::class.javaPrimitiveType?.name == m.returnType.name) {
                                m.invoke(managerObject) as Int
                            } else {
                                m.invoke(managerObject) as Int
                            }
                        } catch (e: Throwable) {
                            e(e)
                        }
                    }
                } catch (e: Throwable) {
                    e(e)
                }
            }
        } catch (e: Throwable) {
            e(e)
        }
        return -1
    }

    fun callGetOrCreateInstance(target: Class<*>): Any? {
        try {
            val array = target.methods
            for (m in array) {
                try {
                    if (Modifier.isPublic(m.modifiers) && Modifier.isStatic(m.modifiers)) {
                        val returnedType = m.returnType
                        if (returnedType == Void.TYPE || returnedType == Any::class.java) continue
                        if (returnedType == target || returnedType == target.superclass || listOf(
                                *target.interfaces
                            ).contains(returnedType)
                        ) {
                            d("Method: " + m.name)
                            try {
                                if (m.parameterTypes.size == 1 && m
                                        .parameterTypes[0].name == Context::class.java.name
                                ) {
                                    //Case for SomeManager.getInstance(Context)
                                    return m.invoke(null, appContext)
                                } else if (m.parameterTypes.isEmpty()) {
                                    //Case for SomeManager.getInstance()
                                    return m.invoke(null)
                                }
                            } catch (e: Throwable) {
                                //FIXME:
                                //Deal with Caused by: java.lang.SecurityException: Permission Denial: get/set setting for user asks to run as user -2 but is calling from user 0; this requires android.permission.INTERACT_ACROSS_USERS_FULL
                                e(e)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    e(e)
                }
            }
        } catch (e: Throwable) {
            e(e)
        }
        try {
            val array = target.constructors
            for (c in array) {
                try {
                    if (Modifier.isPublic(c.modifiers)) {
                        d("Constructor: " + c.name)
                        try {
                            if (c.parameterTypes.size == 1 && c
                                    .parameterTypes[0].name == Context::class.java.name
                            ) {
                                //Case for new SomeManager(Context)
                                return c.newInstance(appContext)
                            } else if (c.parameterTypes.isEmpty()) {
                                //Case for new SomeManager()
                                return c.newInstance()
                            }
                        } catch (e: Throwable) {
                            //FIXME:
                            //Deal with Caused by: java.lang.SecurityException: Permission Denial: get/set setting for user asks to run as user -2 but is calling from user 0; this requires android.permission.INTERACT_ACROSS_USERS_FULL
                            e(e)
                        }
                    }
                } catch (e: Throwable) {
                    e(e)
                }
            }
        } catch (e: Throwable) {
            e(e)
        }
        return null
    }
}