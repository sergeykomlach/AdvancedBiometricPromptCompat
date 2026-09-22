package dev.skomlach.biometric.compat.custom

import android.os.Handler
import androidx.annotation.RestrictTo

/** Callback ownership is checked on the destination thread, not only before posting. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class SoftwareBiometricWorkerCallback(
    private val session: SoftwareBiometricWorkSession,
    private val callback: AbstractSoftwareBiometricManager.AuthenticationCallback?,
    private val onTerminal: () -> Unit = {},
    private val enqueue: (() -> Unit) -> Unit
) : AbstractSoftwareBiometricManager.AuthenticationCallback() {
    init {
        session.bindRevocationDelivery {
            enqueue {
                onTerminal()
                callback?.onAuthenticationCancelled()
            }
        }
    }

    constructor(
        session: SoftwareBiometricWorkSession,
        handler: Handler,
        callback: AbstractSoftwareBiometricManager.AuthenticationCallback?,
        onTerminal: () -> Unit = {}
    ) : this(session, callback, onTerminal, { action -> handler.post { action() }; Unit })

    override fun onAuthenticationCancelled() {
        // Revocation must stop commits before remove/replacement returns, even when
        // the result queue is blocked or already contains a terminal success.
        if (!session.cancel()) return
        enqueue {
            onTerminal()
            callback?.onAuthenticationCancelled()
        }
    }

    override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
        enqueue { if (session.isActive) callback?.onAuthenticationHelp(helpMsgId, helpString) }
    }

    override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
        enqueue {
            if (session.complete()) {
                onTerminal()
                callback?.onAuthenticationError(errMsgId, errString)
            }
        }
    }

    override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) {
        enqueue {
            if (session.complete()) {
                onTerminal()
                callback?.onAuthenticationSucceeded(result)
            }
        }
    }

    override fun onAuthenticationFailed() {
        enqueue { if (session.isActive) callback?.onAuthenticationFailed() }
    }
}
