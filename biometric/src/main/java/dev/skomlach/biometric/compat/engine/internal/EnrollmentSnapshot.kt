package dev.skomlach.biometric.compat.engine.internal

import dev.skomlach.biometric.compat.custom.SoftwareBiometricEnrollment
import java.util.Collections

/** Enumeration is optional for hardware authentication; unavailable never means empty. */
internal sealed class EnrollmentSnapshot {
    class Available(ids: Collection<String>) : EnrollmentSnapshot() {
        val ids: Set<String> = Collections.unmodifiableSet(LinkedHashSet(ids))
    }
    class Unavailable(val cause: Throwable? = null) : EnrollmentSnapshot()
    object Unsupported : EnrollmentSnapshot()
}

internal fun SoftwareBiometricEnrollment.toEnrollmentSnapshot(): EnrollmentSnapshot = when (this) {
    is SoftwareBiometricEnrollment.Available -> EnrollmentSnapshot.Available(ids)
    is SoftwareBiometricEnrollment.Unavailable -> EnrollmentSnapshot.Unavailable(cause)
    SoftwareBiometricEnrollment.Unsupported -> EnrollmentSnapshot.Unsupported
}
