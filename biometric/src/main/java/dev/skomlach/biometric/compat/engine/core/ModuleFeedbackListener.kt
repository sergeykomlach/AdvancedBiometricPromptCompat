package dev.skomlach.biometric.compat.engine.core

import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.StatusAuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.onStatus

/** Adds source/ownership to help callbacks without changing authentication results or crypto. */
internal class ModuleFeedbackListener(
    private val delegate: AuthenticationListener,
    private val source: Int,
    private val isActive: () -> Boolean
) : StatusAuthenticationListener, AuthenticationListener by delegate {
    override fun onHelp(msg: CharSequence?) {
        if (!msg.isNullOrBlank()) onStatus(source, SoftwarePromptStatus(msg))
    }

    override fun onStatus(moduleTag: Int, status: SoftwarePromptStatus) {
        if (isActive()) delegate.onStatus(source, status)
    }
}
