package dev.skomlach.biometric.compat.engine.internal

import dev.skomlach.biometric.compat.custom.SoftwareBiometricEnrollment
import dev.skomlach.common.storage.ProtectedStorageUnavailableException
import org.junit.Assert.*
import org.junit.Test

class SoftwareEnrollmentChangeTrackerTest {
    private val aliceHash = "2BD806C97F0E00AF1A1FC3328FA763A9269723C8DB8FAC4F93AF71DB186D6E90"

    @Test fun encodedEmptyBaselineIsDistinctFromMissingOrMalformedData() {
        assertEquals(emptySet<String>(), decodeEnrollmentBaseline(encodeEnrollmentBaseline(emptySet())))
        for (value in listOf(null, "", emptySet<String>(), "v2\n", "v1\nnot-a-hash")) {
            assertThrows(IllegalArgumentException::class.java) { decodeEnrollmentBaseline(value) }
        }
    }

    @Test fun encodedHashesRoundTripWithoutDependingOnOrder() {
        val first = "A".repeat(64)
        val second = "B".repeat(64)
        val encoded = encodeEnrollmentBaseline(linkedSetOf(second, first))
        assertEquals(encodeEnrollmentBaseline(linkedSetOf(first, second)), encoded)
        assertEquals(setOf(first, second), decodeEnrollmentBaseline(encoded))
        assertThrows(IllegalArgumentException::class.java) {
            decodeEnrollmentBaseline("v1\n$first\n$first")
        }
    }

    @Test fun enrollmentAfterPersistedEmptyBaselineIsAChange() {
        val f = Fixture(listOf("alice"))
        f.baseline = decodeEnrollmentBaseline(encodeEnrollmentBaseline(emptySet()))
        assertEquals(EnrollmentChange.CHANGED, f.tracker.check())
        assertEquals(0, f.writes)
    }

    @Test fun freshInstallCapturesOneCompleteSnapshotWithoutPersistingRawIds() {
        val f = Fixture(listOf("alice"))
        assertEquals(EnrollmentChange.UNCHANGED, f.tracker.check())
        assertEquals(setOf(aliceHash), f.baseline)
        assertEquals(1, f.reads)
        assertEquals(1, f.writes)
        assertNull(f.legacy)
    }

    @Test fun orderAndDuplicateIdsDoNotReportAChange() {
        val f = Fixture(listOf("alice", "bob"))
        f.tracker.check()
        f.available("bob", "alice", "alice")
        assertEquals(EnrollmentChange.UNCHANGED, f.tracker.check())
        assertEquals(1, f.writes)
    }

    @Test fun additionDoesNotAutomaticallyReplaceTheBaseline() {
        val f = Fixture(listOf("alice"))
        f.tracker.check()
        f.available("alice", "bob")
        assertEquals(EnrollmentChange.CHANGED, f.tracker.check())
        assertEquals(setOf(aliceHash), f.baseline)
        assertEquals(1, f.writes)
        assertTrue(f.tracker.lastConfirmedChange)
    }

    @Test fun removalIncludingTheLastEnrollmentIsDetected() {
        val f = Fixture(listOf("alice", "bob"))
        f.tracker.check()
        f.available("alice")
        assertEquals(EnrollmentChange.CHANGED, f.tracker.check())
        f.available()
        assertEquals(EnrollmentChange.CHANGED, f.tracker.check())
        assertEquals(2, f.baseline!!.size)
    }

    @Test fun explicitAcknowledgementAcceptsACompleteEmptySnapshot() {
        val f = Fixture(listOf("alice"))
        f.tracker.check()
        f.available()
        assertEquals(EnrollmentChange.CHANGED, f.tracker.check())
        assertEquals(EnrollmentChange.UNCHANGED, f.tracker.acknowledge())
        assertEquals(emptySet<String>(), f.baseline)
        assertFalse(f.tracker.lastConfirmedChange)
    }

    @Test fun unavailableReadCannotInitializeOrClearASnapshot() {
        val f = Fixture(listOf("alice"))
        f.snapshot = SoftwareBiometricEnrollment.Unavailable()
        assertEquals(EnrollmentChange.UNAVAILABLE, f.tracker.check())
        assertEquals(EnrollmentChange.UNAVAILABLE, f.tracker.acknowledge())
        assertNull(f.baseline)
        assertEquals(0, f.writes)
        assertFalse(f.tracker.lastConfirmedChange)
    }

    @Test fun temporaryFailureRetainsAnExistingBaselineAndRecovers() {
        val f = Fixture(listOf("alice"))
        f.tracker.check()
        f.snapshot = SoftwareBiometricEnrollment.Unavailable()
        assertEquals(EnrollmentChange.UNAVAILABLE, f.tracker.check())
        assertEquals(EnrollmentChange.UNAVAILABLE, f.tracker.acknowledge())
        assertEquals(setOf(aliceHash), f.baseline)
        f.available("alice")
        assertEquals(EnrollmentChange.UNCHANGED, f.tracker.check())
    }

    @Test fun unavailableReadDoesNotEraseAPreviouslyConfirmedChange() {
        val f = Fixture(listOf("alice"))
        f.tracker.check()
        f.available("bob")
        f.tracker.check()
        f.snapshot = SoftwareBiometricEnrollment.Unavailable()
        f.tracker.check()
        f.tracker.acknowledge()
        assertTrue(f.tracker.lastConfirmedChange)
        assertEquals(setOf(aliceHash), f.baseline)
    }

    @Test fun unsupportedProviderNeverTouchesTypedStorage() {
        val f = Fixture(emptyList())
        f.snapshot = SoftwareBiometricEnrollment.Unsupported
        f.readError = ProtectedStorageUnavailableException()
        assertEquals(EnrollmentChange.UNSUPPORTED, f.tracker.check())
        assertEquals(EnrollmentChange.UNSUPPORTED, f.tracker.acknowledge())
        assertEquals(0, f.writes)
        assertTrue(f.errors.isEmpty())
    }

    @Test fun matchingLegacyHashesMigrateWithoutDeletingTheLegacySnapshot() {
        val f = Fixture(listOf("alice"))
        f.legacy = setOf(aliceHash)
        assertEquals(EnrollmentChange.UNCHANGED, f.tracker.check())
        assertEquals(f.legacy, f.baseline)
        assertEquals(setOf(aliceHash), f.legacy)
    }

    @Test fun incomparableLegacyHashesRequireExplicitAcknowledgement() {
        val f = Fixture(listOf("alice"))
        f.legacy = setOf("old reflective identity hash")
        assertEquals(EnrollmentChange.CHANGED, f.tracker.check())
        assertNull(f.baseline)
        assertEquals(0, f.writes)
        assertEquals(EnrollmentChange.UNCHANGED, f.tracker.acknowledge())
        assertEquals(setOf(aliceHash), f.baseline)
        assertEquals(setOf("old reflective identity hash"), f.legacy)
    }

    @Test fun emptyLegacySnapshotCannotSilentlyAcceptExistingEnrollments() {
        val f = Fixture(listOf("alice"))
        f.legacy = emptySet()
        assertEquals(EnrollmentChange.CHANGED, f.tracker.check())
        assertNull(f.baseline)
        assertEquals(0, f.writes)
    }

    @Test fun matchingEmptyLegacySnapshotCanMigrate() {
        val f = Fixture(emptyList())
        f.legacy = emptySet()
        assertEquals(EnrollmentChange.UNCHANGED, f.tracker.check())
        assertEquals(emptySet<String>(), f.baseline)
    }

    @Test fun versionedBaselineTakesPrecedenceOverUnrelatedLegacyData() {
        val f = Fixture(listOf("alice"))
        f.baseline = setOf(aliceHash)
        f.legacyError = IllegalStateException("legacy data cannot be read")
        assertEquals(EnrollmentChange.UNCHANGED, f.tracker.check())
        assertEquals(0, f.writes)
    }

    @Test fun snapshotExceptionsAreUnavailableInsteadOfEmpty() {
        val f = Fixture(listOf("alice"))
        f.snapshotError = ProtectedStorageUnavailableException()
        assertEquals(EnrollmentChange.UNAVAILABLE, f.tracker.check())
        assertNull(f.baseline)
        assertEquals(1, f.errors.size)
    }

    @Test fun unreadableBaselineCannotBeReplacedByACheck() {
        val f = Fixture(listOf("alice"))
        f.readError = ProtectedStorageUnavailableException()
        assertEquals(EnrollmentChange.UNAVAILABLE, f.tracker.check())
        assertEquals(0, f.writes)
    }

    @Test fun unreadableLegacyBaselineCannotBeTreatedAsMissing() {
        val f = Fixture(listOf("alice"))
        f.legacyError = IllegalStateException("wrong preference type")
        assertEquals(EnrollmentChange.UNAVAILABLE, f.tracker.check())
        assertEquals(0, f.writes)
    }

    @Test fun failedCommitCannotBeMistakenForPersistedData() {
        val f = Fixture(listOf("alice"))
        f.failWriteAfterMemoryUpdate = true
        assertEquals(EnrollmentChange.UNAVAILABLE, f.tracker.check())
        assertEquals(setOf(aliceHash), f.baseline) // Simulate Android's failed commit semantics.
        assertEquals(EnrollmentChange.UNAVAILABLE, f.tracker.check())
        f.failWriteAfterMemoryUpdate = false
        assertEquals(EnrollmentChange.UNCHANGED, f.tracker.acknowledge())
        assertEquals(EnrollmentChange.UNCHANGED, f.tracker.check())
    }

    @Test fun failedAcknowledgementRetainsTheConfirmedChange() {
        val f = Fixture(listOf("alice"))
        f.tracker.check()
        f.available("bob")
        f.tracker.check()
        f.failWriteAfterMemoryUpdate = true
        assertEquals(EnrollmentChange.UNAVAILABLE, f.tracker.acknowledge())
        assertTrue(f.tracker.lastConfirmedChange)
    }

    private class Fixture(ids: Collection<String>) {
        var snapshot: SoftwareBiometricEnrollment = SoftwareBiometricEnrollment.Available(ids)
        var baseline: Set<String>? = null
        var legacy: Set<String>? = null
        var reads = 0
        var writes = 0
        var readError: Exception? = null
        var legacyError: Exception? = null
        var snapshotError: Exception? = null
        var failWriteAfterMemoryUpdate = false
        val errors = mutableListOf<Throwable>()
        val tracker = EnrollmentChangeTracker(
            readSnapshot = {
                reads++
                snapshotError?.let { throw it }
                snapshot.toEnrollmentSnapshot()
            },
            readBaseline = {
                readError?.let { throw it }
                baseline
            },
            readLegacyBaseline = {
                legacyError?.let { throw it }
                legacy
            },
            writeBaseline = {
                writes++
                baseline = it.toSet()
                if (failWriteAfterMemoryUpdate) throw ProtectedStorageUnavailableException("commit failed")
            },
            onError = { errors.add(it) }
        )
        fun available(vararg ids: String) { snapshot = SoftwareBiometricEnrollment.Available(ids.toList()) }
    }
}
