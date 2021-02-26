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

package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper

import android.content.ContentResolver
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

object SettingsSystem {
    private var clazz: Class<*>? = null

    init {
        try {
            clazz = Class.forName("android.provider.Settings\$System")
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