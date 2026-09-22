package dev.skomlach.biometric.compat.engine.internal

/** Tracks started providers, not delivered success callbacks, for one whole setup flow. */
internal class EnrollmentRollbackSession<K>(
    private val start: (K) -> EnrollmentRollbackScope,
    private val finish: (K, EnrollmentRollbackScope, Boolean) -> Unit
) {
    private val scopes = LinkedHashMap<K, EnrollmentRollbackScope>()
    private var finished = false

    @Synchronized
    fun track(owner: K): EnrollmentRollbackScope {
        check(!finished) { "Enrollment session is already finished" }
        return scopes.getOrPut(owner) { start(owner) }
    }

    fun finish(succeeded: Boolean) {
        val pending = synchronized(this) {
            if (finished) return
            finished = true
            scopes.toMap().also { scopes.clear() }
        }
        pending.forEach { (owner, scope) -> finish(owner, scope, succeeded) }
    }
}
