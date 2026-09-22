package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

/** Decode only when persisted enrollment changes; never use a cache when storage is unreadable. */
internal class FaceTemplateCache<T>(
    private val readPayload: () -> String?,
    private val decode: (String?) -> T
) {
    private data class Snapshot<T>(val payload: String?, val value: T)
    private var cached: Snapshot<T>? = null

    @Synchronized
    fun snapshot(): T {
        // Read first, including on cache hits: a failed protected-storage read must propagate.
        val payload = readPayload()
        cached?.takeIf { it.payload == payload }?.let { return it.value }
        val value = decode(payload)
        cached = Snapshot(payload, value)
        return value
    }
}
