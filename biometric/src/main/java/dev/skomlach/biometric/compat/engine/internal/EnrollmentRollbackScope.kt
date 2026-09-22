package dev.skomlach.biometric.compat.engine.internal

/** Every setup path needs an explicit, fresh tag; null is a destructive remove-all sentinel. */
internal fun <T> provisionalEnrollment(create: (String) -> T): T =
    create(java.util.UUID.randomUUID().toString())

/** Owns only templates created by one provisional enrollment, including retries. */
internal class EnrollmentRollbackScope {
    private var succeeded: Boolean? = null
    private val actions = ArrayList<() -> Unit>()

    fun record(rollback: () -> Unit) {
        val rollbackNow = synchronized(this) {
            when (succeeded) {
                null -> { actions.add(rollback); false }
                true -> false
                false -> true
            }
        }
        if (rollbackNow) rollback()
    }

    fun finish(succeeded: Boolean) {
        val pending = synchronized(this) {
            if (this.succeeded != null) return
            this.succeeded = succeeded
            actions.toList().also { actions.clear() }
        }
        if (!succeeded) pending.forEach { it() }
    }
}
