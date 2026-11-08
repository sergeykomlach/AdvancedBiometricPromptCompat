package dev.skomlach.biometric.compat

/** Coalesces terminal/UI-close events and completes cleanup before any client callback. */
internal class AuthFlowCompletion(
    private val post: (() -> Unit) -> Unit,
    private val ownsFlow: () -> Boolean,
    private val cleanup: () -> Unit,
    private val release: () -> Boolean,
    private val onClosed: () -> Unit,
    private val afterCleanup: ((() -> Unit) -> Unit) = { it() }
) {
    private var queued = false
    private var completed = false
    private var terminal: (() -> Unit)? = null

    @Synchronized
    fun isFinishing(): Boolean = queued || completed

    @Synchronized
    fun finish(callback: (() -> Unit)? = null) {
        if (completed) return
        if (terminal == null) terminal = callback
        if (queued) return
        queued = true
        post {
            val dispatch = synchronized(this) {
                completed = true
                terminal
            }
            if (ownsFlow()) {
                try {
                    cleanup()
                } finally {
                    afterCleanup {
                        if (ownsFlow() && release()) {
                            try {
                                dispatch?.invoke()
                            } finally {
                                onClosed()
                            }
                        }
                    }
                }
            }
        }
    }
}
