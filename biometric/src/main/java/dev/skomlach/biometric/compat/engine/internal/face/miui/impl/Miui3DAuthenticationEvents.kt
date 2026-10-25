package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

internal class Miui3DAuthenticationEvents {
    private val callbacks = MiuiAuthenticationCallbacks()

    fun begin(
        callback: IMiuiFaceManager.AuthenticationCallback,
        enqueue: (() -> Unit) -> Unit,
        onTerminal: () -> Unit
    ): Attempt {
        val owned = object : IMiuiFaceManager.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(face: Miuiface?) {
                onTerminal()
                callback.onAuthenticationSucceeded(face)
            }
            override fun onAuthenticationFailed() {
                onTerminal()
                callback.onAuthenticationFailed()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                onTerminal()
                callback.onAuthenticationError(errorCode, errString)
            }
            override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) =
                callback.onAuthenticationHelp(helpCode, helpString)
        }
        return Attempt(callbacks.begin(owned, enqueue))
    }

    class Attempt internal constructor(private val callback: MiuiAuthenticationCallbacks.Attempt) {
        val isActive: Boolean get() = callback.isActive
        fun cancel(): Boolean = callback.cancel()
        fun timeout() = callback.failed(terminal = true)
        fun error(code: Int, message: CharSequence?) = callback.error(code, message)
        fun onEvent(module: Int, event: Int, arg1: Int, arg2: Int) {
            if (module != 1) return
            when (event) {
                40 -> callback.succeeded(null)
                41 -> callback.help(70, null)
                42 -> callback.help(arg1, null)
            }
        }
    }
}
