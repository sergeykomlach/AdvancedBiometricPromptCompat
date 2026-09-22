package dev.skomlach.biometric.compat.custom

import androidx.annotation.RestrictTo

/**
 * One process-local enrollment namespace, shared by all its managers and maintenance stores.
 * Commits, terminal claims and removal use the same monitor. Never run inference or application
 * callbacks under [lock]. This is not cross-process coordination.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class SoftwareBiometricWorkScope {
    val lock = Any()
    private val sessions = mutableSetOf<SoftwareBiometricWorkSession>()

    fun newSession(): SoftwareBiometricWorkSession = synchronized(lock) {
        SoftwareBiometricWorkSession(lock) { sessions.remove(it) }.also(sessions::add)
    }

    /** Even a failed deletion revokes previously admitted work, without swallowing its error. */
    fun revoke(remove: () -> Unit) {
        val deliveries = mutableListOf<() -> Unit>()
        var failure = runCatching {
            synchronized(lock) {
                sessions.toList().forEach { session -> session.revoke()?.let(deliveries::add) }
                remove()
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
