package dev.skomlach.biometric.compat.engine.internal

/** Shared across module instances because a failed Android commit may still mutate memory. */
internal class EnrollmentBaselineStore(
    private val readValue: () -> Set<String>?,
    private val commitValue: (Set<String>) -> Boolean
) {
    private var writeFailed = false

    @Synchronized
    fun read(): Set<String>? {
        check(!writeFailed) { "Enrollment baseline persistence unavailable" }
        return readValue()?.toSet()
    }

    @Synchronized
    fun write(value: Set<String>) {
        writeFailed = true
        check(commitValue(value.toSet())) { "Enrollment baseline commit failed" }
        writeFailed = false
    }
}
