package dev.skomlach.biometric.compat.engine.internal.face

import java.util.concurrent.atomic.AtomicLong

/** Bind vendor events and queued delivery to the authentication attempt that owns them. */
internal class FaceAuthenticationRequestGate<C>(private val enqueue: (() -> Unit) -> Unit) {
    private class Owner<C>(val requestId: Int, val callback: C, val enqueue: (() -> Unit) -> Unit)
    private val lock = Any()
    private var active: Owner<C>? = null

    fun begin(callback: C): Int = begin(callback, enqueue)

    fun begin(callback: C, enqueue: (() -> Unit) -> Unit): Int = synchronized(lock) {
        val id = nextId.incrementAndGet()
        check(id <= Int.MAX_VALUE) { "Face request IDs exhausted" }
        id.toInt().also { active = Owner(it, callback, enqueue) }
    }

    fun isActive(requestId: Int): Boolean = synchronized(lock) { active?.requestId == requestId }

    /** Invalidate before invoking vendor cancellation, which can call back synchronously. */
    fun cancel(requestId: Int? = null): Int? = synchronized(lock) {
        if (requestId != null && active?.requestId != requestId) null
        else active?.requestId.also { active = null }
    }

    fun dispatch(requestId: Int, terminal: Boolean, delivery: (C) -> Unit) {
        val owner = synchronized(lock) { active?.takeIf { it.requestId == requestId } } ?: return
        owner.enqueue {
            val callback = synchronized(lock) {
                if (active !== owner) null
                else owner.callback.also { if (terminal) active = null }
            }
            // Consume a terminal owner before calling user code, including reentrant authenticate().
            callback?.let(delivery)
        }
    }

    private companion object {
        val nextId = AtomicLong()
    }
}
