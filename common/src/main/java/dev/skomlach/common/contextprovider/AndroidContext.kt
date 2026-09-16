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
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.lifecycle.MutableLiveData
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.LocaleHelper
import java.io.IOException
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object AndroidContext {
    private val currentApplicationMethod: Method? by lazy {
        runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
        }.getOrNull()
    }
    private val initialApplicationMethod: Method? by lazy {
        runCatching {
            Class.forName("android.app.AppGlobals")
                .getMethod("getInitialApplication")
        }.getOrNull()
    }

    private val resumedActivities = ResumedActivityState<Activity>()
    val resumedActivityLiveData = MutableLiveData<Activity?>()
    private val configurationRelay = AtomicReference<Configuration?>(null)
    private val configurationMutableLiveData = MutableLiveData<Unit>(null)
    val configurationLiveData = configurationMutableLiveData
    private val dirAccessFixStarted = AtomicBoolean(false)

    private val componentCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            updateConfiguration(newConfig)
        }

        override fun onLowMemory() {}
    }
    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            updateConfiguration(activity.resources.configuration)
        }

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {
            resumedActivities.resume(activity)
            publishResumedActivity()
            updateConfiguration(activity.resources.configuration)
        }

        override fun onActivityPaused(activity: Activity) {
            removeResumedActivity(activity)
        }

        override fun onActivityStopped(activity: Activity) {
            removeResumedActivity(activity)
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {
            removeResumedActivity(activity)
        }
    }

    // Accessed only by ApplicationReference's serialized initialization callback.
    private var componentCallbacksRegistered = false
    private var activityCallbacksRegistered = false
    private val applicationReference = ApplicationReference(
        resolve = ::getApplicationContext,
        onAvailable = ::registerApplicationCallbacks,
        onError = { LogCat.logException(it, "AndroidContext") }
    )

    val activity: Activity?
        get() = resumedActivities.current

    var appConfiguration: Configuration? = null
        get() = configurationRelay.get() ?: appContext.resources.configuration
        private set

    var systemConfiguration: Configuration? = null
        get() = Resources.getSystem().configuration
        private set

    val appInstance: Application?
        get() = applicationReference.getOrNull()?.also(::scheduleDirAccessFix)

    val appContext: Context
        get() = appInstance ?: throw RuntimeException("Application is NULL")

    val appLocale: Locale
        get() = LocaleHelper.getDefault(appContext)

    val systemLocale: Locale
        get() = LocaleHelper.systemLocale(appContext)

    private fun getApplicationContext(): Application? {
        return runCatching {
            currentApplicationMethod?.invoke(null) as? Application
        }.getOrNull() ?: runCatching {
            initialApplicationMethod?.invoke(null) as? Application
        }.getOrNull()
    }

    private fun registerApplicationCallbacks(application: Application) {
        // Application synchronizes both registration APIs. Register on the caller's thread:
        // waiting for Main here can deadlock the first access during object initialization.
        // Keep each step idempotent if a later step fails and the next access retries it.
        if (!componentCallbacksRegistered) {
            application.registerComponentCallbacks(componentCallbacks)
            componentCallbacksRegistered = true
        }
        if (!activityCallbacksRegistered) {
            application.registerActivityLifecycleCallbacks(activityCallbacks)
            activityCallbacksRegistered = true
        }
        updateConfiguration(application.resources.configuration)
    }

    private fun updateConfiguration(configuration: Configuration) {
        val snapshot = Configuration(configuration)
        val previous = configurationRelay.getAndSet(snapshot)
        if (previous == null || previous.diff(snapshot) != 0) {
            configurationMutableLiveData.postValue(Unit)
        }
    }

    private fun removeResumedActivity(activity: Activity) {
        resumedActivities.remove(activity)
        publishResumedActivity()
    }

    private fun publishResumedActivity() {
        // Application delivers lifecycle callbacks on Main. Publish synchronously so a
        // rapid resume/pause cannot leave LiveData holding an already paused Activity.
        val current = resumedActivities.current
        if (resumedActivityLiveData.value !== current) {
            resumedActivityLiveData.value = current
        }
    }

    private fun scheduleDirAccessFix(context: Context) {
        if (dirAccessFixStarted.compareAndSet(false, true)) {
            try {
                ExecutorHelper.startOnBackground {
                    fixDirAccess(context)
                }
            } catch (error: Throwable) {
                dirAccessFixStarted.set(false)
                LogCat.logException(error, "AndroidContext")
            }
        }
    }

    private fun fixDirAccess(context: Context) {
        // Retain the owner-only data directory protection (0700) without hidden FileUtils.
        try {
            restrictAccessToOwnerOnly(context.applicationInfo.dataDir)
        } catch (_: Throwable) {
            // Preserve the existing best-effort behavior on filesystems that reject chmod.
        }
    }

    @Throws(IOException::class)
    private fun restrictAccessToOwnerOnly(path: String) {
        try {
            Os.chmod(path, OsConstants.S_IRWXU)
        } catch (error: ErrnoException) {
            throw IOException("Failed to restrict data directory permissions", error)
        }
    }

    init {
        // A very early access must not poison this singleton if Application is not ready yet.
        // appContext/appInstance will retry discovery on the next access.
        appInstance?.let { LogCat.logError("Pkg ${it.packageName}") }
    }
}
