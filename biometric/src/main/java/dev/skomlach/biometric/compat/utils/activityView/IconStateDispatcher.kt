package dev.skomlach.biometric.compat.utils.activityView

import dev.skomlach.biometric.compat.BiometricType
import java.util.IdentityHashMap

/** Queued events belong to a registration, never to the next dialog's listeners. */
internal class IconStateDispatcher(
    private val post: (Runnable) -> Unit,
    private val postDelayed: (Runnable, Long) -> Unit,
    private val remove: (Runnable) -> Unit
) {
    private class Registration(var listener: IconStateHelper.IconStateListener?) {
        val tasks = mutableSetOf<Runnable>()
        val resets = mutableMapOf<BiometricType?, Runnable>()
        var refresh: Runnable? = null
    }

    private val registrations = IdentityHashMap<IconStateHelper.IconStateListener, Registration>()

    @Synchronized fun register(listener: IconStateHelper.IconStateListener) {
        if (!registrations.containsKey(listener)) registrations[listener] = Registration(listener)
    }

    @Synchronized fun unregister(listener: IconStateHelper.IconStateListener) {
        val registration = registrations.remove(listener) ?: return
        registration.listener = null
        registration.tasks.forEach(remove)
        registration.tasks.clear()
        registration.resets.clear()
        registration.refresh = null
    }

    @Synchronized fun error(type: BiometricType?) {
        registrations.values.toList().forEach { registration ->
            enqueue(registration) { listener ->
                cancelReset(registration, type)
                listener.onError(type)
                if (registration.listener != null) {
                    registration.resets[type] = enqueue(registration, 2_000L) {
                        registration.resets.remove(type)
                        it.reset(type)
                    }
                }
            }
        }
    }

    @Synchronized fun success(type: BiometricType?) {
        registrations.values.toList().forEach { registration ->
            enqueue(registration) {
                cancelReset(registration, type)
                it.onSuccess(type)
            }
        }
    }

    @Synchronized fun refreshAvailability() {
        registrations.values.toList().forEach { registration ->
            if (registration.refresh == null) {
                registration.refresh = enqueue(registration) {
                    registration.refresh = null
                    it.onAvailabilityChanged()
                }
            }
        }
    }

    private fun cancelReset(registration: Registration, type: BiometricType?) {
        registration.resets.remove(type)?.let {
            registration.tasks.remove(it)
            remove(it)
        }
    }

    private fun enqueue(
        registration: Registration,
        delay: Long = 0L,
        event: (IconStateHelper.IconStateListener) -> Unit
    ): Runnable {
        lateinit var task: Runnable
        task = Runnable {
            synchronized(this) {
                // Also reject a removed Runnable that had already been dequeued by the Handler.
                if (registration.tasks.remove(task)) registration.listener?.let(event)
            }
        }
        registration.tasks.add(task)
        if (delay == 0L) post(task) else postDelayed(task, delay)
        return task
    }
}
