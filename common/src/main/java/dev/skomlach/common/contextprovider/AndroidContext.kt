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

package dev.skomlach.common.contextprovider

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import androidx.core.os.ConfigurationCompat
import java.io.IOException
import java.util.*

@SuppressLint("StaticFieldLeak")
object AndroidContext {
    private var appRef: Application? = null
        private set(value) {
            field = value
            field?.registerActivityLifecycleCallbacks(ActivityContextProvider)
            ctxRef = field
        }

    private var ctxRef: Context? = null
        set(value) {
            field = try {
                value?.getFixedContext()
            } catch (e: Throwable) {
                e.printStackTrace()
                value
            }
        }

    var configuration: Configuration? = null
        get() {
            return ActivityContextProvider.configuration ?: appRef?.resources?.configuration
        }
        private set

    val appInstance: Application
        get() {
            return if (ctxRef is Application)
                ctxRef as Application
            else
                ctxRef?.applicationContext as Application
        }

    val appContext: Context
        get() {
            ctxRef?.let {
                fixDirAccess(it)
                return it
            }
            if (Looper.getMainLooper().thread !== Thread.currentThread()) throw IllegalThreadStateException(
                "Main thread required for correct init"
            )
            appRef = try {
                Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) as Application
            } catch (ignored: Throwable) {
                try {
                    Class.forName("android.app.AppGlobals")
                        .getMethod("getInitialApplication")
                        .invoke(null) as Application
                } catch (e: Throwable) {
                    throw RuntimeException(e)
                }
            }
            ctxRef?.let {
                fixDirAccess(it)
                return it
            }
            throw RuntimeException("Application is NULL")
        }

    //Solution from
    //https://github.com/google/google-authenticator-android/
    private fun fixDirAccess(context: Context) {
        // Try to restrict data dir file permissions to owner (this app's UID) only. This mitigates the
        // security vulnerability where SQLite database transaction journals are world-readable.
        // See CVE-2011-3901 advisory for more information.
        // NOTE: This also prevents all files in the data dir from being world-accessible, which is fine
        // because this application does not need world-accessible files.
        try {
            restrictAccessToOwnerOnly(context.applicationInfo.dataDir)
        } catch (e: Throwable) {
            // Ignore this exception and don't log anything to avoid attracting attention to this fix
        }
    }

    /**
     * Restricts the file permissions of the provided path so that only the owner (UID)
     * can access it.
     */
    @Throws(IOException::class)
    private fun restrictAccessToOwnerOnly(path: String) {
        // IMPLEMENTATION NOTE: The code below simply invokes the hidden API
        // android.os.FileUtils.setPermissions(path, 0700, -1, -1) via Reflection.
        val errorCode: Int = try {
            Class.forName("android.os.FileUtils")
                .getMethod(
                    "setPermissions",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                .invoke(null, path, 448, -1, -1) as Int
        } catch (e: Exception) {
            // Can't chain exception because IOException doesn't have the right constructor on Froyo
            // and below
            throw IOException("Failed to set permissions: $e")
        }
        if (errorCode != 0) {
            throw IOException("setPermissions failed with error code $errorCode")
        }
    }

    val locale: Locale
        get() {
            val listCompat = ConfigurationCompat.getLocales(
                appContext.resources.configuration
            )
            var l = if (!listCompat.isEmpty) listCompat[0] else Locale.getDefault()
            if (l == null) {
                l = Locale.getDefault()
            }
            return l
        }
}