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

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.lang.reflect.Modifier


object ReflectionUtils {
    fun printClass(builder: StringBuilder, name: String?) {
        try {
            // print class name and superclass name (if != Object)
            val cl = Class.forName(name)
            printClass(builder, cl)
        } catch (ignore: ClassNotFoundException) {
        }
    }

    fun printClass(builder: StringBuilder, cl: Class<*>) {
        try {
            val supercl = cl.superclass
            val modifiers = Modifier.toString(cl.modifiers)
            if (modifiers.isNotEmpty()) {
                builder.append("$modifiers ")
            }
            builder.append("class " + cl.name)
            if (supercl != null && supercl != Any::class.java) {
                builder.append(
                    " extends "
                            + supercl.name
                )
            }
            builder.append("\n{\n")
            printConstructors(builder, cl)
            builder.append("\n")
            printFields(builder, cl)
            builder.append("\n")
            printMethods(builder, cl)
            builder.append("\n")
            printClasses(builder, cl)
            builder.append("\n}\n")
        } catch (e: Exception) {
            e(e)
        }
    }

    /**
     * Prints all constructors of a class
     *
     * @param cl a class
     */
    fun printConstructors(builder: StringBuilder, cl: Class<*>) {
        val constructors = cl.declaredConstructors
        for (c in constructors) {
            val name = c.name
            builder.append("   ")
            val modifiers = Modifier.toString(c.modifiers)
            if (modifiers.isNotEmpty()) {
                builder.append("$modifiers ")
            }
            builder.append("$name(")

            // print parameter types
            val paramTypes = c.parameterTypes
            for (j in paramTypes.indices) {
                if (j > 0) {
                    builder.append(", ")
                }
                builder.append(paramTypes[j].name)
            }
            builder.append(");\n")
        }
    }

    /**
     * Prints all methods of a class
     *
     * @param cl a class
     */
    fun printMethods(builder: StringBuilder, cl: Class<*>) {
        val methods = cl.declaredMethods
        for (m in methods) {
            val retType = m.returnType
            val name = m.name
            builder.append("   ")
            // print modifiers, return type and method name
            val modifiers = Modifier.toString(m.modifiers)
            if (modifiers.isNotEmpty()) {
                builder.append("$modifiers ")
            }
            builder.append(retType.name + " " + name + "(")

            // print parameter types
            val paramTypes = m.parameterTypes
            for (j in paramTypes.indices) {
                if (j > 0) {
                    builder.append(", ")
                }
                builder.append(paramTypes[j].name)
            }
            builder.append(");\n")
        }
    }

    /**
     * Prints all fields of a class
     *
     * @param cl a class
     */
    @Throws(IllegalAccessException::class)
    fun printFields(builder: StringBuilder, cl: Class<*>) {
        val fields = cl.declaredFields
        for (f in fields) {
            val isAccessible = f.isAccessible
            if (!isAccessible) {
                f.isAccessible = true
            }
            val type = f.type
            val name = f.name
            builder.append("   ")
            val modifiers = Modifier.toString(f.modifiers)
            if (modifiers.isNotEmpty()) {
                builder.append("$modifiers ")
            }
            if (Modifier.isStatic(f.modifiers)) {
                if (type == String::class.java)
                    builder.append(type.name + " " + name + " = \"" + f[null] + "\";\n") else builder.append(
                    type.name + " " + name + " = " + f[null] + ";\n"
                )
            } else builder.append(type.name + " " + name + ";\n")
            if (!isAccessible) {
                f.isAccessible = true
            }
        }
    }

    /**
     * Prints all fields of a class
     *
     * @param cl a class
     */
    @Throws(IllegalAccessException::class)
    fun printClasses(builder: StringBuilder, cl: Class<*>) {
        val fields = cl.declaredClasses
        for (f in fields) {
            printClass(builder, f)
        }
    }
}