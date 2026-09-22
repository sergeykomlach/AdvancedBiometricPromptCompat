package dev.skomlach.biometric.compat.custom

import androidx.annotation.RestrictTo
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * One authentication attempt. Expensive inference runs outside this monitor; only short
 * state commits run inside it. After cancel() returns, rollback cannot race a later write.
 * Never invoke application callbacks or wait for a worker while holding this monitor.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class SoftwareBiometricWorkSession internal constructor(
    private val monitor: Any,
    private val onInactive: (SoftwareBiometricWorkSession) -> Unit
) {
    constructor() : this(Any(), {})

    private var active = true
    private var revoked = false
    private var revocationDelivery: (() -> Unit)? = null
    val isActive: Boolean get() = synchronized(monitor) { active }

    fun <T> runIfActive(action: () -> T): T? =
        synchronized(monitor) { if (active) action() else null }

    fun cancel(): Boolean = synchronized(monitor) {
        if (!active) return@synchronized false
        active = false
        revocationDelivery = null
        onInactive(this)
        true
    }

    // Terminal claim is ordered against removal. Application delivery follows outside the lock.
    fun complete(): Boolean = cancel()

    internal fun bindRevocationDelivery(delivery: () -> Unit) {
        val deliverNow = synchronized(monitor) {
            if (active) revocationDelivery = delivery
            revoked
        }
        if (deliverNow) delivery()
    }

    /** Only the scope calls this, under the shared monitor; delivery must run outside it. */
    internal fun revoke(): (() -> Unit)? = synchronized(monitor) {
        if (!active) return@synchronized null
        val delivery = revocationDelivery
        revoked = true
        cancel()
        delivery
    }
}

/** Serial native/model access without retaining an idle thread for every manager instance. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun newSoftwareBiometricWorker(name: String): Executor = ThreadPoolExecutor(
    0, 1, 5L, TimeUnit.SECONDS, LinkedBlockingQueue(),
    { task -> Thread(task, name).apply { isDaemon = true } }
)
