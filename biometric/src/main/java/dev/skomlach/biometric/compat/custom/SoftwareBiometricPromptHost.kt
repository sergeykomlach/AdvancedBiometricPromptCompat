package dev.skomlach.biometric.compat.custom

import android.content.Context
import android.os.Bundle
import android.view.View
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricPromptCompat

data class SoftwareBiometricPromptHost(
    val context: Context,
    val builder: BiometricPromptCompat.Builder,
    val enroll: Boolean,
    val rootView: View?,
    val callbacks: Callbacks
) {
    interface Callbacks {
        fun onHelp(message: CharSequence)

        fun onReady(extras: Bundle? = null)

        fun onFailure(result: AuthenticationResult)

        fun isPromptActive(): Boolean
    }
}
