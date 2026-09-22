package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkSession

/** Routes vendor callbacks to the native worker without carrying them into a later session. */
internal class ZkFingerCaptureSession(
    private val onInvalidated: (ZkFingerCaptureSession) -> Unit = {},
    private val enqueue: (() -> Unit) -> Unit
) {
    private val operation = SoftwareBiometricWorkSession()
    private val delivery = SoftwareBiometricWorkSession()

    val isActive: Boolean get() = operation.isActive

    // Bind at registration time; never look up the manager's mutable current session
    // from a vendor callback (the vendor can invoke it after close()).
    fun <T> bind(consume: (T) -> Unit): (T) -> Unit = { value -> post { consume(value) } }

    fun bind(action: () -> Unit): () -> Unit = { post(action) }

    fun bindTemplate(consume: (ByteArray) -> Unit): (ByteArray?) -> Unit =
        { template -> postTemplate(template, consume) }

    fun post(action: () -> Unit) {
        if (!isActive) return
        enqueue { if (isActive) action() }
    }

    fun postTemplate(template: ByteArray?, consume: (ByteArray) -> Unit) {
        if (!isActive || template == null) return
        // Vendor SDKs may reuse the callback buffer as soon as extractOK returns.
        val snapshot = template.copyOf()
        post { consume(snapshot) }
    }

    /** Native inference stays outside this monitor; only short state commits belong inside. */
    fun <T> commit(action: () -> T): T? = operation.runIfActive(action)

    /** SDK cleanup may precede delivery of the terminal result that caused it. */
    fun stopCapture() { operation.cancel() }

    fun postCallback(enqueueResult: (() -> Unit) -> Unit, terminal: Boolean = false, action: () -> Unit) {
        if (!delivery.isActive) return
        enqueueResult {
            if (if (terminal) delivery.complete() else delivery.isActive) action()
        }
    }

    /** Cancel/remove/replacement revokes both future commits and already queued callbacks. */
    fun invalidate(): Boolean {
        operation.cancel()
        val revoked = delivery.cancel()
        if (revoked) onInvalidated(this)
        return revoked
    }
}
