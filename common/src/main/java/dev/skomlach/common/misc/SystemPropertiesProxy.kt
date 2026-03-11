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

/**
 * This class cannot be instantiated
 */
object SystemPropertiesProxy {
    private val systemPropertiesClass: Class<*>? by lazy {
        try {
            Class.forName("android.os.SystemProperties")
        } catch (e: Exception) {
            null
        }
    }

    private val getMethod by lazy {
        systemPropertiesClass?.getMethod("get", String::class.java)
    }

    private val getWithDefMethod by lazy {
        systemPropertiesClass?.getMethod("get", String::class.java, String::class.java)
    }

    @Throws(IllegalArgumentException::class)
    fun get(context: Context, key: String): String {
        return try {
            (getMethod?.invoke(null, key) as? String) ?: ""
        } catch (iAE: IllegalArgumentException) {
            throw iAE
        } catch (e: Exception) {
            ""
        }
    }

    @Throws(IllegalArgumentException::class)
    fun get(context: Context, key: String, def: String): String {
        return try {
            (getWithDefMethod?.invoke(null, key, def) as? String) ?: def
        } catch (iAE: IllegalArgumentException) {
            throw iAE
        } catch (e: Exception) {
            def
        }
    }
}