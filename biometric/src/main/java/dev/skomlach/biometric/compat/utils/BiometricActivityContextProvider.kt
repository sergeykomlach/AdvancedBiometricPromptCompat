package dev.skomlach.biometric.compat.utils

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import dev.skomlach.biometric.compat.BiometricApi
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


object BiometricActivityContextProvider : ActivityLifecycleCallbacks {

    private var job: Job? = null
    private val backgroundThreadExecutor: ExecutorService = Executors.newCachedThreadPool()
    private var backgroundScope =
        CoroutineScope(backgroundThreadExecutor.asCoroutineDispatcher())

    private fun checkBiometric() {
        if (job?.isActive == true) {
            job?.cancel()
            job = null
        }
        job = backgroundScope.launch {
            for (type in BiometricType.values()) {
                for (api in BiometricApi.values()) {
                    try {
                        BiometricManagerCompat.isBiometricReadyForUsage(
                            BiometricAuthRequest(
                                api = api,
                                type = type
                            )
                        )
                    } catch (ignore: Throwable) {
                    }
                }
            }
        }

    }

    val listener = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            super.onFragmentResumed(fm, f)
            checkBiometric()
        }

    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener {
                    if (it)
                        checkBiometric()
                }
            }
            if (activity is FragmentActivity) {
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(listener, true)
            }
        } catch (ignore: Throwable) {
        }
    }

    override fun onActivityStarted(activity: Activity) {

    }

    override fun onActivityResumed(activity: Activity) {
        checkBiometric()
    }

    override fun onActivityPaused(activity: Activity) {

    }

    override fun onActivityStopped(activity: Activity) {

    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

    }

    override fun onActivityDestroyed(activity: Activity) {

    }
}