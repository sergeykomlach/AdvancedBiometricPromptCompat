package dev.skomlach.biometric.compat.custom

import androidx.annotation.RestrictTo

/**
 * One process-local enrollment namespace, shared by all its managers and maintenance stores.
 * Commits, terminal claims and removal use the same monitor. Never run inference or application
 * callbacks under [lock]. This is not cross-process coordination.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class SoftwareBiometricWorkScope(private val nonBlockingCancellation: Boolean = false) {
    val lock = Any()
    private val admission = if (nonBlockingCancellation) Any() else lock
    private val sessionsLock = Any()
    private val sessions = LinkedHashSet<SoftwareBiometricWorkSession>()
    private var removing = false

    fun newSession(): SoftwareBiometricWorkSession = synchronized(admission) {
        val session = SoftwareBiometricWorkSession(lock, { ended ->
            synchronized(sessionsLock) { sessions.remove(ended) }
        }, nonBlockingCancellation)
        if (removing) session.revoke() else synchronized(sessionsLock) { sessions.add(session) }
        session
    }

    /** Even a failed deletion revokes previously admitted work, without swallowing its error. */
    fun revoke(remove: () -> Unit) {
        val deliveries = mutableListOf<() -> Unit>()
        var failure = runCatching {
            synchronized(lock) {
                val current = synchronized(admission) {
                    removing = true
                    synchronized(sessionsLock) { sessions.toList() }
                }
                try {
                    current.forEach { session -> session.revoke()?.let(deliveries::add) }
                    remove()
                } finally {
                    synchronized(admission) { removing = false }
                }
            }
        }.exceptionOrNull()
        deliveries.forEach { deliver ->
            try { deliver() }
            catch (error: Throwable) {
                val previous = failure
                if (previous == null) failure = error
                else if (previous !== error) previous.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}
