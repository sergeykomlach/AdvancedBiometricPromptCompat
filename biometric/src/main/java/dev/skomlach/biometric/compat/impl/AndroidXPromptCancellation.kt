package dev.skomlach.biometric.compat.impl

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.MainThread
import androidx.biometric.BiometricFragment
import androidx.biometric.BiometricPrompt
import androidx.biometric.CancellationHelper
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl

/** Keeps the AndroidX-internal fallback in one adapter; authentication never depends on lookup. */
@SuppressLint("RestrictedApi")
@MainThread
internal class AndroidXPromptCancellation(
    private val fragmentManager: FragmentManager,
    prompt: BiometricPrompt,
    private val ownsSession: () -> Boolean
) {
    private val cancellation = PromptCancellation<BiometricFragment>(
        cancelPrompt = prompt::cancelAuthentication,
        cancelFragment = CancellationHelper::forceCancel,
        onError = { BiometricLoggerImpl.e(it, "AndroidXPromptCancellation") }
    )
    private var observing = false
    private var closed = false
    private val observer = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentPreAttached(fm: FragmentManager, fragment: Fragment, context: Context) {
            capture(fragment)
        }
    }

    fun observe() {
        if (closed || observing || !ownsSession()) return
        fragmentManager.registerFragmentLifecycleCallbacks(observer, false)
        observing = true
        // AndroidX can reuse a fragment from the preceding completed attempt.
        fragmentManager.fragments.forEach(::capture)
    }

    private fun capture(fragment: Fragment) {
        if (!closed && ownsSession() && fragment is BiometricFragment) {
            cancellation.capture(fragment)
        }
        // Do not clear on detach: the retained reference is the cancellation fallback.
    }

    fun cancel() {
        if (closed) return
        closed = true
        try {
            if (observing) fragmentManager.unregisterFragmentLifecycleCallbacks(observer)
        } finally {
            observing = false
            cancellation.cancel()
        }
    }
}
