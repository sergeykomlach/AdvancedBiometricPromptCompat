package dev.skomlach.biometric.compat.custom

import dev.skomlach.common.storage.ProtectedStorageUnavailableException
import java.util.Collections

/** A complete snapshot of enrollment IDs, independent of camera permission or engine readiness. */
sealed class SoftwareBiometricEnrollment {
    /** IDs identify membership, not changes to template contents under an existing ID. */
    class Available(ids: Collection<String>) : SoftwareBiometricEnrollment() {
        val ids: Set<String> = Collections.unmodifiableSet(LinkedHashSet(ids))
    }

    /** Reading failed. This must never be converted into an empty snapshot. */
    class Unavailable(val cause: Throwable? = null) : SoftwareBiometricEnrollment()

    /** An older provider has not opted into the complete snapshot contract. */
    object Unsupported : SoftwareBiometricEnrollment()

    companion object {
        /** Copy all IDs before reporting success; a partially read collection is unavailable. */
        fun read(reader: () -> Collection<String>): SoftwareBiometricEnrollment = try {
            Available(reader())
        } catch (error: Exception) {
            Unavailable(error)
        } catch (error: LinkageError) {
            Unavailable(error)
        }
    }
}

/** The old boolean enrollment API cannot express unavailability; enforce it on the software route. */
internal fun SoftwareBiometricEnrollment.requireReadable() {
    if (this is SoftwareBiometricEnrollment.Unavailable) {
        throw ProtectedStorageUnavailableException("Software enrollment snapshot is unavailable", cause)
    }
}
