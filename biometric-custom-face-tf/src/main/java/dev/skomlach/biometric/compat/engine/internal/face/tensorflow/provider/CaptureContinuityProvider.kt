package dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider

/** Optional extension: keep IFrameProvider's existing JVM contract intact. */
interface CaptureContinuityProvider {
    fun setCaptureDiscontinuityListener(listener: (() -> Unit)?)
}
