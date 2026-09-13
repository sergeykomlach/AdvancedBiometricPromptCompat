package dev.skomlach.biometric.compat.utils

/** One worker and one pending immutable snapshot. Older writes cannot overtake newer ones. */
internal class LatestSnapshotWriter<T : Any>(
    private val execute: (Runnable) -> Unit,
    private val write: (T) -> Unit,
    private val onFailure: (Throwable) -> Unit
) {
    private var pending: T? = null
    private var running = false

    fun offer(snapshot: T) {
        synchronized(this) {
            pending = snapshot
            if (running) return
            running = true
        }
        execute(Runnable {
            while (true) {
                val next = synchronized(this) {
                    val value = pending
                    pending = null
                    if (value == null) running = false
                    value
                } ?: break
                try { write(next) } catch (error: Throwable) { onFailure(error) }
            }
        })
    }
}

/** Bound both entry count and retained text, including caller-supplied very long strings. */
internal fun boundPromptTextEntries(entries: Map<String, String?>): Map<String, String?> {
    val result = LinkedHashMap<String, String?>()
    var characters = 0
    entries.forEach { (key, value) ->
        val size = key.length + (value?.length ?: 0)
        if (size <= 4_096) {
            result[key] = value
            characters += size
            while (result.size > 128 || characters > 65_536) {
                val oldest = result.entries.iterator().next()
                characters -= oldest.key.length + (oldest.value?.length ?: 0)
                result.remove(oldest.key)
            }
        }
    }
    return result
}
