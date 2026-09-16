package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

/** A loading/partial OEM database must not look like a successfully read empty database. */
internal class CompleteEnrollmentSnapshot<T : Any> {
    @Volatile private var value: List<T>? = null

    fun invalidate() { value = null }

    fun publish(entries: List<T?>?) {
        value = null
        if (entries == null) return
        val complete = ArrayList<T>(entries.size)
        for (entry in entries) complete.add(entry ?: return)
        value = complete
    }

    fun read(ready: Boolean): List<T>? = if (ready) value?.toList() else null
}
