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

package dev.skomlach.biometric.compat.utils.appstate

import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.skomlach.biometric.compat.impl.IBiometricPromptImpl
import dev.skomlach.biometric.compat.impl.PendingAuthStart
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.atomic.AtomicInteger

class AppBackgroundDetector(val impl: IBiometricPromptImpl, callback: () -> Unit) {
    private var stopWatcher: Runnable? = null
    private var observedHost: androidx.fragment.app.FragmentActivity? = null
    private val pendingHostDismiss = PendingAuthStart(ExecutorHelper::postDelayed, ExecutorHelper::removeCallbacks)
    private val hostLifecycleObserver = LifecycleEventObserver { _, event ->
        if (shouldCancelPendingLifecycleDismiss(event)) {
            pendingHostDismiss.cancel()
        } else if (shouldDismissForHostLifecycle(event)) {
            pendingHostDismiss.schedule(0) { if (stopWatcher != null) callback() }
        }
    }
    private val pendingFragmentDismiss = PendingAuthStart(ExecutorHelper::postDelayed, ExecutorHelper::removeCallbacks)
    private var fragmentDismissPaused = false
    private val pendingLifecycleDismiss = PendingAuthStart(ExecutorHelper::postDelayed, ExecutorHelper::removeCallbacks)
    private val homeWatcher = HomeWatcher(object : HomeWatcher.OnHomePressedListener {
        override fun onHomePressed() {
            if (stopWatcher != null)
                callback.invoke()
        }

        override fun onRecentAppPressed() {
            if (stopWatcher != null)
                callback.invoke()
        }

        override fun onPowerPressed() {
            if (stopWatcher != null)
                callback.invoke()
        }
    })
    private val fragmentLifecycleCallbacks = object :
        FragmentManager.FragmentLifecycleCallbacks() {
        private val atomicBoolean = AtomicInteger(0)
        fun reset() {
            atomicBoolean.set(0)
        }
        private val dismissTask = Runnable {
            if (atomicBoolean.get() <= 0) {
                BiometricLoggerImpl.e("fragmentLifecycleCallbacks.AppBackgroundDetector.dismissTask")
                if (stopWatcher != null)
                    callback.invoke()
            }
        }

        @SuppressLint("RestrictedApi")
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            if (f is androidx.biometric.BiometricFragment ||
                f is androidx.biometric.FingerprintDialogFragment ||
                f is dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialog
            ) {
                BiometricLoggerImpl.d(
                    "AppBackgroundDetector.FragmentLifecycleCallbacks.onFragmentResumed - " +
                            "$f"
                )
                pendingFragmentDismiss.cancel()
                atomicBoolean.incrementAndGet()
            }
        }

        @SuppressLint("RestrictedApi")
        override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
            if (f is androidx.biometric.BiometricFragment ||
                f is androidx.biometric.FingerprintDialogFragment ||
                f is dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialog
            ) {
                BiometricLoggerImpl.d(
                    "AppBackgroundDetector.FragmentLifecycleCallbacks.onFragmentPaused - " +
                            "$f"
                )
                atomicBoolean.decrementAndGet()
                pendingFragmentDismiss.cancel()
                if (fragmentDismissPaused) return
                val delay =
                    impl.builder.getContext().resources.getInteger(android.R.integer.config_longAnimTime)
                        .toLong()
                pendingFragmentDismiss.schedule(delay) { dismissTask.run() }//delay for case when system fragment closed and fallback shown
            }
        }
    }

    private val lifecycleEventObserver: LifecycleEventObserver = object : LifecycleEventObserver {
        private val dismissTask = Runnable {
            BiometricLoggerImpl.e("lifecycleEventObserver.AppBackgroundDetector.dismissTask")
            if (stopWatcher != null)
                callback.invoke()
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (shouldCancelPendingLifecycleDismiss(event)) {
                pendingLifecycleDismiss.cancel()
                return
            }
            when (event) {
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                    pendingLifecycleDismiss.cancel()
                    val delay =
                        impl.builder.getContext().resources.getInteger(android.R.integer.config_longAnimTime)
                            .toLong()
                    pendingLifecycleDismiss.schedule(delay) { dismissTask.run() }//delay for case when system fragment closed and fallback shown
                }

                else -> {}
            }
        }
    }

    fun attachListeners() {
        detachListeners()
        // Another Activity may remain resumed in split-screen or a Bubble task. Observe
        // this authentication host as well as the process; PAUSE/focus loss is not invisibility.
        observedHost = impl.builder.getActivity()
        observedHost?.lifecycle?.addObserver(hostLifecycleObserver)
        try {
            observedHost?.supportFragmentManager?.unregisterFragmentLifecycleCallbacks(
                fragmentLifecycleCallbacks
            )
        } catch (ignore: Throwable) {
        }
        try {
            observedHost?.supportFragmentManager?.registerFragmentLifecycleCallbacks(
                fragmentLifecycleCallbacks,
                false
            )
        } catch (ignore: Throwable) {
        }
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleEventObserver)
        } catch (ignore: Throwable) {
        }
        stopWatcher = homeWatcher.startWatch()
    }

    internal fun cancelPendingFragmentDismiss() {
        pendingFragmentDismiss.cancel()
    }

    internal fun pauseFragmentDismiss() {
        fragmentDismissPaused = true
        cancelPendingFragmentDismiss()
    }

    internal fun resumeFragmentDismiss() {
        fragmentDismissPaused = false
    }

    fun detachListeners() {
        pendingHostDismiss.cancel()
        fragmentDismissPaused = false
        pendingFragmentDismiss.cancel()
        pendingLifecycleDismiss.cancel()
        fragmentLifecycleCallbacks.reset()
        stopWatcher?.run()
        stopWatcher = null
        try {
            observedHost?.supportFragmentManager?.unregisterFragmentLifecycleCallbacks(
                fragmentLifecycleCallbacks
            )
        } catch (ignore: Throwable) {
        }
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleEventObserver)
        } catch (ignore: Throwable) {
        }
        observedHost?.lifecycle?.removeObserver(hostLifecycleObserver)
        observedHost = null
    }
}

internal fun shouldCancelPendingLifecycleDismiss(event: Lifecycle.Event): Boolean {
    return event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME
}

internal fun shouldDismissForHostLifecycle(event: Lifecycle.Event): Boolean =
    event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY
