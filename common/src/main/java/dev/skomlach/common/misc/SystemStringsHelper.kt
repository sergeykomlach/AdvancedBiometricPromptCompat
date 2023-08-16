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

package dev.skomlach.common.misc

import android.content.Context
import dev.skomlach.common.logging.LogCat


object SystemStringsHelper {
    fun getFromSystem(context: Context, alias: String): String? {
        try {
            val fields = Class.forName("com.android.internal.R\$string").declaredFields
            for (field in fields) {
                if (field.name.equals(alias)) {
                    LogCat.log("BiometricTitle", field.name)
                    val isAccessible = field.isAccessible
                    return try {
                        if (!isAccessible) field.isAccessible = true
                        val s = context.getString(field[null] as Int)
                        if (s.isEmpty())
                            throw RuntimeException("String is empty")
                        s
                    } finally {
                        if (!isAccessible) field.isAccessible = false
                    }
                }
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        return null
    }

}