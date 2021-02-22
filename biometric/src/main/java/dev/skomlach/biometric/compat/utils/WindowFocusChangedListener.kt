package dev.skomlach.biometric.compat.utils

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface WindowFocusChangedListener {
    fun onStartWatching()
    fun hasFocus(hasFocus: Boolean)
}