package dev.skomlach.common.contextprovider

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.res.Configuration
import android.os.Bundle

object ActivityContextProvider : ActivityLifecycleCallbacks {
    var configuration: Configuration? = null
        private set

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        configuration = activity.resources.configuration
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {
        configuration = activity.resources.configuration
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}