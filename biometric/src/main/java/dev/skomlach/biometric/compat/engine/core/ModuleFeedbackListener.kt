package dev.skomlach.biometric.compat.engine.core

import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.StatusAuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.onStatus
import dev.skomlach.biometric.compat.impl.PendingAuthStart
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.os.CancellationSignal

/** Covers direct OEM callers as well as Core and preserves a listener across internal retries. */
internal class ModuleAuthenticationRequests(private val source: Int) {
    private var current: ModuleFeedbackListener? = null

    fun bind(listener: AuthenticationListener?, signal: CancellationSignal?): ModuleFeedbackListener {
        if (listener is ModuleFeedbackListener && !listener.isAuthenticationActive()) return listener
        val next = listener as? ModuleFeedbackListener ?: ModuleFeedbackListener(
            listener, source, cancelRequest = { signal?.cancel() }
        ) { signal?.isCanceled != true }
        val previous = synchronized(this) {
            if (current === next) return next
            current.also {
                current = next
                it?.supersede()
            }
        }
        // SDK cancellation can reenter start; no registry monitor may be held here.
        previous?.cancelAuthentication()
        return next
    }
}

/** Ownership survives cancellation but ends before a replacement can start. */
internal class ModuleRequestOwner {
    private val current = AtomicBoolean(true)
    fun isCurrent(): Boolean = current.get()
    fun supersede() { current.set(false) }
}

/** Binds feedback and successful results to the originating Core request. */
internal class ModuleFeedbackListener(
    private val delegate: AuthenticationListener?,
    private val source: Int,
    private val owner: ModuleRequestOwner = ModuleRequestOwner(),
    private val cancelRequest: () -> Unit = {},
    post: (Runnable, Long) -> Unit = { task, delay -> ExecutorHelper.postDelayed(task, delay) },
    remove: (Runnable) -> Unit = { ExecutorHelper.removeCallbacks(it) },
    private val isActive: () -> Boolean
) : StatusAuthenticationListener {
    private val pendingCancel = PendingAuthStart(post, remove)
    fun isCurrentRequest(): Boolean = owner.isCurrent()
    fun isAuthenticationActive(): Boolean = isCurrentRequest() && isActive()
    fun cancelAuthentication() = cancelRequest()
    fun supersede() {
        owner.supersede()
    }

    fun postCancelTask(task: Runnable) {
        if (!isCurrentRequest()) return
        pendingCancel.schedule(2_000L) { if (isCurrentRequest()) task.run() }
    }

    override fun onSuccess(moduleTag: Int, biometricCryptoObject: BiometricCryptoObject?) {
        if (isAuthenticationActive()) delegate?.onSuccess(moduleTag, biometricCryptoObject)
    }

    // Some providers cancel their Core signal before reporting a genuine terminal error.
    override fun onFailure(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) {
        if (isCurrentRequest()) delegate?.onFailure(moduleTag, reason, description)
    }

    override fun onCanceled(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) {
        if (isCurrentRequest()) delegate?.onCanceled(moduleTag, reason, description)
    }

    override fun onHelp(msg: CharSequence?) {
        if (!msg.isNullOrBlank()) onStatus(source, SoftwarePromptStatus(msg))
    }

    override fun onStatus(moduleTag: Int, status: SoftwarePromptStatus) {
        if (isAuthenticationActive()) delegate?.onStatus(source, status)
    }
}
