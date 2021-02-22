package dev.skomlach.biometric.compat.engine

import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import dev.skomlach.biometric.compat.BiometricType

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface BiometricAuthenticationListener {
    //user identity confirmed in module
    @WorkerThread
    fun onSuccess(module: BiometricType?)

    @WorkerThread
    fun onHelp(helpReason: AuthenticationHelpReason?, msg: CharSequence?)

    //failure happens in module
    @WorkerThread
    fun onFailure(
        failureReason: AuthenticationFailureReason?,
        module: BiometricType?
    )
}