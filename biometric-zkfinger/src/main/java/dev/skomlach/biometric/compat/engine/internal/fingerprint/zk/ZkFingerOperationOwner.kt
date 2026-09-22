package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

/** Shared by every manager because both templates and the vendor engine are process-wide. */
internal class ZkFingerOperationOwner {
    private var current: ZkFingerCaptureSession? = null

    @Synchronized
    fun start(create: () -> ZkFingerCaptureSession): ZkFingerCaptureSession {
        current?.invalidate()
        return create().also { current = it }
    }

    @Synchronized
    fun revoke(remove: (ZkFingerCaptureSession?) -> Unit) {
        val retired = current
        current = null
        retired?.invalidate()
        // Invalidation waits for a short in-flight commit before storage is removed.
        // Queue native cleanup here too, before a new operation can be submitted.
        remove(retired)
    }

    @Synchronized
    fun release(session: ZkFingerCaptureSession) {
        if (current === session) current = null
    }
}
