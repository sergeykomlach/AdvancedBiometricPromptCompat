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

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

object MiuiBuild {
    var IS_INTERNATIONAL_BUILD = false
    var DEVICE: String? = null
    private var clazz: Class<*>? = null

    init {
        try {
            clazz = Class.forName("miui.os.Build")
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