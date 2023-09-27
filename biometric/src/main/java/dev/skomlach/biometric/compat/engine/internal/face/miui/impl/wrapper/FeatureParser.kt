/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

object FeatureParser {
    private var clazz: Class<*>? = null

    init {
        try {
            clazz = Class.forName("miui.util.FeatureParser")
        } catch (ignored: ClassNotFoundException) {
        } catch (e: Throwable) {
            e(e)
        }
    }

    fun getStringArray(s: String?): Array<String>? {
        return try {
            clazz?.getMethod("getStringArray", String::class.java)
                ?.invoke(null, s) as Array<String>?
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