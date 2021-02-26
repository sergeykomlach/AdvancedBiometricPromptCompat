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
import android.database.ContentObserver
import android.net.Uri
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

object ContentResolverHelper {
    private var clazz: Class<*>? = null

    init {
        try {
            clazz = Class.forName("android.content.ContentResolver")
        } catch (e: Throwable) {
            e(e)
        }
    }

    fun registerContentObserver(
        cr: ContentResolver, uri: Uri, notifyForDescendents: Boolean,
        observer: ContentObserver, userHandle: Int
    ) {
        try {
            clazz?.getMethod(
                "registerContentObserver",
                Uri::class.java,
                Boolean::class.javaPrimitiveType,
                ContentObserver::class.java,
                Int::class.javaPrimitiveType
            )?.invoke(cr, uri, notifyForDescendents, observer, userHandle)
        } catch (e: Throwable) {
            e(e)
            try {
                cr.registerContentObserver(uri, notifyForDescendents, observer)
            } catch (e2: Throwable) {
                e(e2)
            }
        }
    }
}