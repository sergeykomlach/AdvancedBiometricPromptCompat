package dev.skomlach.biometric.compat.engine.internal

/** Capture before posting work; a queued vendor event must never look up a replacement callback. */
internal class OemCallbackSession<T : Any> {
    class Capture<T : Any> internal constructor(val callback: T)
    @Volatile private var current: Capture<T>? = null

    @Synchronized fun begin(callback: T): Capture<T> = Capture(callback).also { current = it }
    fun current(): Capture<T>? = current
    fun owns(capture: Capture<T>): Boolean = current === capture
    @Synchronized fun clear() { current = null }

    fun deliver(capture: Capture<T>, event: (T) -> Unit) {
        if (owns(capture)) event(capture.callback)
    }
}
