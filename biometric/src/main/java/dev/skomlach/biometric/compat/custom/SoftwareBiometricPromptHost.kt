package dev.skomlach.biometric.compat.custom

import android.content.Context
import android.os.Bundle
import android.view.View
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricPromptCompat

data class SoftwarePromptStatus(
    val primaryText: CharSequence,
    val secondaryText: CharSequence? = null,
    val terminal: Boolean = false,
    /**
     * Keep an actionable instruction visible until this source advances or finishes.
     * While a system prompt is active, the shared host retains all non-terminal legacy
     * help/status messages automatically; this opt-in is only needed outside that mode.
     */
    val persistent: Boolean = false
) {
    // Retain the original JVM constructor/default-mask and copy/default-mask signatures for
    // already compiled providers. The fourth field is opt-in and source-compatible.
    constructor(primaryText: CharSequence, secondaryText: CharSequence? = null, terminal: Boolean = false) :
        this(primaryText, secondaryText, terminal, false)

    fun copy(primaryText: CharSequence = this.primaryText, secondaryText: CharSequence? = this.secondaryText,
             terminal: Boolean = this.terminal): SoftwarePromptStatus =
        SoftwarePromptStatus(primaryText, secondaryText, terminal, persistent)

    fun asLegacyHelpMessage(): CharSequence {
        val secondary = secondaryText?.takeIf { it.isNotBlank() } ?: return primaryText
        return buildString {
            append(primaryText)
            if (isNotEmpty()) {
                append('\n')
            }
            append(secondary)
        }
    }
}

data class SoftwareBiometricPromptHost(
    val context: Context,
    val builder: BiometricPromptCompat.Builder,
    val enroll: Boolean,
    val rootView: View?,
    val callbacks: Callbacks
) {
    interface Callbacks {
        fun onHelp(message: CharSequence)

        fun onStatus(status: SoftwarePromptStatus) {
            onHelp(status.asLegacyHelpMessage())
        }

        fun onReady(extras: Bundle? = null)

        fun onFailure(result: AuthenticationResult)

        fun isPromptActive(): Boolean
    }
}
