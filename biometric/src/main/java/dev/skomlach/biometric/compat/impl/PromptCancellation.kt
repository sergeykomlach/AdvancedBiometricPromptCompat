package dev.skomlach.biometric.compat.impl

/** One prompt attempt. Its cached fallback remains usable after the fragment is detached. */
internal class PromptCancellation<T>(
    private val cancelPrompt: () -> Unit,
    private val cancelFragment: (T) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private var closed = false
    private var fragment: T? = null

    fun capture(fragment: T) {
        if (!closed) this.fragment = fragment
    }

    fun cancel() {
        if (closed) return
        closed = true
        val captured = fragment
        fragment = null
        // Close ownership before external code can re-enter or deliver another fragment.
        try {
            runCatching(cancelPrompt).onFailure(onError)
        } finally {
            if (captured != null) runCatching { cancelFragment(captured) }.onFailure(onError)
        }
    }
}
