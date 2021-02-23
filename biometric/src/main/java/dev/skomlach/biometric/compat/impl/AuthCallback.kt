package dev.skomlach.biometric.compat.impl

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface AuthCallback {
    fun startAuth()
    fun stopAuth()
    fun cancelAuth()
    fun onUiOpened()
    fun onUiClosed()
}