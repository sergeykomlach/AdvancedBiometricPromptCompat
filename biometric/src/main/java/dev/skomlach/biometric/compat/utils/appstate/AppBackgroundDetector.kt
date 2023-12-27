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
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.skomlach.biometric.compat.impl.IBiometricPromptImpl
import dev.skomlach.biometric.compat.utils.ScreenProtection
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.atomic.AtomicInteger

class AppBackgroundDetector(val impl: IBiometricPromptImpl, callback: () -> Unit) {
    private var stopWatcher: Runnable? = null
    private val homeWatcher = HomeWatcher(object : HomeWatcher.OnHomePressedListener {
        override fun onHomePressed() {
            callback.invoke()
        }

        override fun onRecentAppPressed() {
            callback.invoke()
        }

        override fun onPowerPressed() {
            callback.invoke()
        }
    })
    private val fragmentLifecycleCallbacks = object :
        FragmentManager.FragmentLifecycleCallbacks() {
        private val atomicBoolean = AtomicInteger(0)
        private val dismissTask = Runnable {
            if (atomicBoolean.get() <= 0) {
                BiometricLoggerImpl.e("fragmentLifecycleCallbacks.AppBackgroundDetector.dismissTask")
                callback.invoke()
            }
        }

        @SuppressLint("RestrictedApi")
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            if (f is androidx.biometric.BiometricFragment ||
                f is androidx.biometric.FingerprintDialogFragment ||
                f is dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialog
            ) {
                BiometricLoggerImpl.d(
                    "AppBackgroundDetector.FragmentLifecycleCallbacks.onFragmentResumed - " +
                            "$f"
                )
                atomicBoolean.incrementAndGet()
                try {
                    ScreenProtection.applyProtectionInView(f.requireView())
                    if(f is DialogFragment){
                        ScreenProtection.applyProtectionInWindow(f.dialog?.window)
                    }
                } catch (e :Throwable){

                }
            }
        }

        @SuppressLint("RestrictedApi")
        override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
            if (f is androidx.biometric.BiometricFragment ||
                f is androidx.biometric.FingerprintDialogFragment ||
                f is dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialog
            ) {
                BiometricLoggerImpl.d(
                    "AppBackgroundDetector.FragmentLifecycleCallbacks.onFragmentPaused - " +
                            "$f"
                )
                atomicBoolean.decrementAndGet()
                ExecutorHelper.removeCallbacks(dismissTask)
                val delay =
                    impl.builder.getContext().resources.getInteger(android.R.integer.config_longAnimTime)
                        .toLong()
                ExecutorHelper.postDelayed(
                    dismissTask,
                    delay
                )//delay for case when system fragment closed and fallback shown
            }
        }
    }

    private val lifecycleEventObserver: LifecycleEventObserver = object : LifecycleEventObserver {
        private val dismissTask = Runnable {
            BiometricLoggerImpl.e("lifecycleEventObserver.AppBackgroundDetector.dismissTask")
            callback.invoke()
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                    ExecutorHelper.removeCallbacks(dismissTask)
                    val delay =
                        impl.builder.getContext().resources.getInteger(android.R.integer.config_longAnimTime)
                            .toLong()
                    ExecutorHelper.postDelayed(
                        dismissTask,
                        delay
                    )//delay for case when system fragment closed and fallback shown
                }

                else -> {}
            }
        }
    }

    fun attachListeners() {
        detachListeners()
        try {
            impl.builder.getActivity()?.supportFragmentManager?.unregisterFragmentLifecycleCallbacks(
                fragmentLifecycleCallbacks
            )
        } catch (ignore: Throwable) {
        }
        try {
            impl.builder.getActivity()?.supportFragmentManager?.registerFragmentLifecycleCallbacks(
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

    fun detachListeners() {
        stopWatcher?.run()
        stopWatcher = null
        try {
            impl.builder.getActivity()?.supportFragmentManager?.unregisterFragmentLifecycleCallbacks(
                fragmentLifecycleCallbacks
            )
        } catch (ignore: Throwable) {
        }
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleEventObserver)
        } catch (ignore: Throwable) {
        }
    }
}