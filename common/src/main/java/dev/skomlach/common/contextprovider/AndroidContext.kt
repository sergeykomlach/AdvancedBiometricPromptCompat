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

package dev.skomlach.common.contextprovider

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock


object AndroidContext {
    private val _resumedActivityLiveData = MutableLiveData<Reference<Activity?>>()
    val resumedActivityLiveData = MutableLiveData<Activity?>()
    private val configurationRelay = AtomicReference<Reference<Configuration?>?>(null)
    private val configurationMutableLiveData = MutableLiveData<Unit>(null)
    val configurationLiveData = configurationMutableLiveData

    init {
        ExecutorHelper.post {
            try {
                _resumedActivityLiveData.observeForever {
                    resumedActivityLiveData.postValue(it.get())
                }
            } catch (_: Throwable) {

            }
        }
    }

    val activity: Activity?
        get() = try {
            _resumedActivityLiveData.value?.get()
        } catch (e: Throwable) {
            null
        }
    private val lock = ReentrantLock()
    private var appRef = AtomicReference<Reference<Application?>?>(null)
    private fun getContextRef(): Context? = appRef.get()?.get()

    var appConfiguration: Configuration? = null
        get() {
            return configurationRelay.get()?.get() ?: appContext.resources.configuration
        }
        private set
    var systemConfiguration: Configuration? = null
        get() {
            return Resources.getSystem().configuration
        }
        private set

    val appInstance: Application? = appRef.get()?.get()

    val appContext: Context
        get() {
            getContextRef()?.let {
                ExecutorHelper.startOnBackground {
                    fixDirAccess(it)
                }
                return it
            } ?: run {
                try {
                    lock.runCatching { this.lock() }
                    if (Looper.getMainLooper().thread !== Thread.currentThread()) {
                        runBlocking {
                            withContext(Dispatchers.Main) {
                                updateApplicationReference()
                            }
                        }
                    } else {
                        updateApplicationReference()
                    }
                    getContextRef()?.let {
                        ExecutorHelper.startOnBackground {
                            fixDirAccess(it)
                        }
                        return it
                    }
                    throw RuntimeException("Application is NULL")
                } finally {
                    lock.runCatching {
                        this.unlock()
                    }
                }
            }
        }

    val appLocale: Locale
        get() {
            return LocaleHelper.getDefault(appContext)
        }
    val systemLocale: Locale
        get() {
            return LocaleHelper.systemLocale(appContext)
        }

    private fun getApplicationContext(): Application? {
        return try {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as Application
        } catch (ignored: Throwable) {
            try {
                Class.forName("android.app.AppGlobals")
                    .getMethod("getInitialApplication")
                    .invoke(null) as Application
            } catch (e: Throwable) {
                null
            }
        }
    }

    private fun updateApplicationReference() {
        if (Looper.getMainLooper().thread !== Thread.currentThread())
            throw IllegalThreadStateException("Main thread required for correct init")
        appRef.set(
            SoftReference<Application?>(
                getApplicationContext()?.also {
                    configurationRelay.set(SoftReference(it.resources.configuration))
                    configurationMutableLiveData.postValue(Unit)
                    it.registerComponentCallbacks(object : ComponentCallbacks {
                        override fun onConfigurationChanged(newConfig: Configuration) {
                            LogCat.logError("AndroidContext", "onConfigurationChanged $newConfig")
                            configurationRelay.set(SoftReference(newConfig))
                            configurationMutableLiveData.postValue(Unit)
                        }

                        override fun onLowMemory() {}
                    })
                    it.registerActivityLifecycleCallbacks(object :
                        Application.ActivityLifecycleCallbacks {
                        override fun onActivityCreated(
                            activity: Activity,
                            savedInstanceState: Bundle?
                        ) {
                            LogCat.logError(
                                "AndroidContext",
                                "onConfigurationChanged ${activity.resources.configuration}"
                            )
                            configurationRelay.set(SoftReference(activity.resources.configuration))
                            configurationMutableLiveData.postValue(Unit)
                        }

                        override fun onActivityStarted(activity: Activity) {}
                        override fun onActivityResumed(activity: Activity) {
                            configurationRelay.set(SoftReference(activity.resources.configuration))
                            configurationMutableLiveData.postValue(Unit)
                            _resumedActivityLiveData.postValue(SoftReference(activity))
                        }

                        override fun onActivityPaused(activity: Activity) {
                            if (activity !== resumedActivityLiveData.value) {
                                LogCat.logError(
                                    "AndroidContext", "Another activity already resumed"
                                )
                            } else {
                                _resumedActivityLiveData.postValue(SoftReference(null))
                            }
                            LogCat.logError(
                                "AndroidContext",
                                "onActivityPaused: ${activity.javaClass.simpleName}"
                            )
                        }

                        override fun onActivityStopped(activity: Activity) {}
                        override fun onActivitySaveInstanceState(
                            activity: Activity,
                            outState: Bundle
                        ) {
                        }

                        override fun onActivityDestroyed(activity: Activity) {
                        }
                    })
                }
            )
        )
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
    init {
        val context = appContext
        LogCat.logError("Pkg ${context.packageName}")
    }
}