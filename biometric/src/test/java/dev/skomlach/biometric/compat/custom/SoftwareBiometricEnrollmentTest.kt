package dev.skomlach.biometric.compat.custom

import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.internal.readSoftwareModuleState
import dev.skomlach.common.storage.ProtectedStorageUnavailableException
import org.junit.Assert.*
import org.junit.Test

class SoftwareBiometricEnrollmentTest {
    @Test fun availableSnapshotOwnsAnImmutableCopy() {
        val source = mutableListOf("one", "two", "one")
        val snapshot = SoftwareBiometricEnrollment.read { source } as SoftwareBiometricEnrollment.Available
        source.clear()

        assertEquals(setOf("one", "two"), snapshot.ids)
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.ids as MutableSet<String>).clear()
        }
    }

    @Test fun emptyCollectionIsAnAvailableSnapshot() {
        val snapshot = SoftwareBiometricEnrollment.read { emptyList() }
        assertTrue(snapshot is SoftwareBiometricEnrollment.Available)
        assertTrue((snapshot as SoftwareBiometricEnrollment.Available).ids.isEmpty())
    }

    @Test fun storageFailureIsUnavailableAndRetainsTheCause() {
        val failure = ProtectedStorageUnavailableException()
        val snapshot = SoftwareBiometricEnrollment.read { throw failure }
        assertSame(failure, (snapshot as SoftwareBiometricEnrollment.Unavailable).cause)
    }

    @Test fun incompleteIterationIsUnavailable() {
        val brokenIds = object : AbstractCollection<String>() {
            override val size = 2
            override fun iterator(): Iterator<String> = object : Iterator<String> {
                private var first = true
                override fun hasNext() = true
                override fun next(): String {
                    if (!first) error("read failed halfway")
                    first = false
                    return "one"
                }
            }
        }
        assertTrue(SoftwareBiometricEnrollment.read { brokenIds } is SoftwareBiometricEnrollment.Unavailable)
    }

    @Test fun providerLinkageFailureIsUnavailable() {
        val failure = NoSuchMethodError("optional provider method")
        val snapshot = SoftwareBiometricEnrollment.read { throw failure }
        assertSame(failure, (snapshot as SoftwareBiometricEnrollment.Unavailable).cause)
    }

    @Test fun unavailableEnrollmentCannotAdvertiseAnEnrollmentReadySoftwareRoute() {
        val state = readSoftwareModuleState(
            managerPresent = true,
            moduleLockedOut = false,
            hardwareDetected = { true },
            hasEnrollment = {
                SoftwareBiometricEnrollment.Unavailable().requireReadable()
                error("unavailable enrollment must stop the read")
            },
            lockoutError = { error("must not be read") },
            onError = { assertTrue(it is ProtectedStorageUnavailableException) }
        )
        assertTrue(state.hardwarePresent)
        assertFalse(state.managerAccessible)
        assertFalse(state.enrolled)
        assertTrue(state.lockedOut)
    }

    @Test fun readableAndUnsupportedProvidersKeepTheirAuthenticationPath() {
        SoftwareBiometricEnrollment.Available(emptyList()).requireReadable()
        SoftwareBiometricEnrollment.Unsupported.requireReadable()
    }

    @Test fun oldManagerIsUnsupportedWithoutCallingItsLegacyMethods() {
        val manager = object : AbstractSoftwareBiometricManager() {
            override val biometricType = BiometricType.BIOMETRIC_FACE
            override fun getTimeoutMessage(): CharSequence? = null
            override fun resetLockOut() = Unit
            override fun resetPermanentLockOut() = Unit
            override fun getPermissions(): List<String> = emptyList()
            override fun isHardwareDetected() = true
            override fun hasEnrolledBiometric() = true
            override fun getManagers(): Set<Any> = error("legacy discovery must remain lazy")
            override fun getEnrolls(): Collection<String> = error("not opted in")
            override fun remove(extra: Bundle?) = Unit
            override fun getEnrollBundle(name: String?): Bundle = error("unused")
            override fun authenticate(crypto: CryptoObject?, flags: Int, cancel: CancellationSignal?,
                callback: AuthenticationCallback?, handler: Handler?, extra: Bundle?) = Unit
        }
        assertSame(SoftwareBiometricEnrollment.Unsupported, manager.getEnrollmentSnapshot())
    }
}
