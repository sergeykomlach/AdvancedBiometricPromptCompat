package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkSession

/** One capture attempt plus continuity epochs; a gap also invalidates in-flight inference. */
internal class FaceCaptureSession(val operation: SoftwareBiometricWorkSession) {
    private var generation = 0L
    private var framePending = false

    @Synchronized fun acquireFrame(): Long? {
        if (!operation.isActive || framePending) return null
        framePending = true
        return generation
    }

    @Synchronized fun discontinue() { generation++ }
    @Synchronized fun releaseFrame() { framePending = false }
    @Synchronized fun owns(ticket: Long): Boolean = operation.isActive && generation == ticket

    @Synchronized fun <T> commit(ticket: Long, action: () -> T): T? =
        if (generation == ticket) operation.runIfActive(action) else null
}
