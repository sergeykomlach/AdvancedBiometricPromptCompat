package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

import dev.skomlach.biometric.compat.engine.internal.face.FaceAuthenticationRequestGate

internal class MiuiAuthenticationCallbacks {
    private val requests = FaceAuthenticationRequestGate<IMiuiFaceManager.AuthenticationCallback> { it() }

    fun begin(callback: IMiuiFaceManager.AuthenticationCallback, enqueue: (() -> Unit) -> Unit): Attempt =
        Attempt(requests, requests.begin(callback, enqueue))

    fun cancel() { requests.cancel() }

    class Attempt internal constructor(
        private val requests: FaceAuthenticationRequestGate<IMiuiFaceManager.AuthenticationCallback>,
        private val id: Int
    ) {
        val isActive: Boolean get() = requests.isActive(id)
        fun cancel(): Boolean = requests.cancel(id) != null
        fun succeeded(face: Miuiface?) = requests.dispatch(id, true) { it.onAuthenticationSucceeded(face) }
        fun failed(terminal: Boolean = false) = requests.dispatch(id, terminal) { it.onAuthenticationFailed() }
        fun error(code: Int, message: CharSequence?) =
            requests.dispatch(id, true) { it.onAuthenticationError(code, message) }
        fun help(code: Int, message: CharSequence?) =
            requests.dispatch(id, false) { it.onAuthenticationHelp(code, message) }
    }
}
