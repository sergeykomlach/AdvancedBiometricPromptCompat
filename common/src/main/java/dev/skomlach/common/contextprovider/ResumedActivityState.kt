package dev.skomlach.common.contextprovider

import java.lang.ref.WeakReference

/** The most recently resumed activity, retaining other resumed windows as a fallback. */
internal class ResumedActivityState<T : Any> {
    private val resumed = ArrayList<WeakReference<T>>()

    val current: T?
        @Synchronized get() {
            while (resumed.isNotEmpty()) {
                resumed.last().get()?.let { return it }
                resumed.removeAt(resumed.lastIndex)
            }
            return null
        }

    @Synchronized
    fun resume(activity: T) {
        remove(activity)
        resumed.add(WeakReference(activity))
    }

    @Synchronized
    fun remove(activity: T) {
        resumed.removeAll { reference ->
            val candidate = reference.get()
            candidate == null || candidate === activity
        }
    }
}
