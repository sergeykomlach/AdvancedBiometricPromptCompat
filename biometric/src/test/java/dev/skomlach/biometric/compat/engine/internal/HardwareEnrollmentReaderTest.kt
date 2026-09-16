package dev.skomlach.biometric.compat.engine.internal

import org.junit.Assert.*
import org.junit.Test

class HardwareEnrollmentReaderTest {
    @Test fun fingerprintReadsTheCurrentUserNoArgumentMethodOnly() {
        val manager = FingerprintManager(listOf(OldFingerprint(1)))
        assertEquals(setOf(hardwareEnrollmentId(7, 9L, 1)), ids(FingerprintEnrollmentReader.read(manager)))
        assertEquals(1, manager.calls)
        assertEquals(0, manager.otherCalls)
    }

    @Test fun inheritedModernIdentifierDoesNotDependOnClassNameOrDisplayName() {
        assertEquals(ids(FingerprintEnrollmentReader.read(FingerprintManager(listOf(ModernFingerprint(2))))),
            ids(FingerprintEnrollmentReader.read(FingerprintManager(listOf(RenamedFingerprint(2))))))
    }

    @Test fun renamedOldDisplayNameDoesNotChangeIdentity() {
        assertEquals(ids(FingerprintEnrollmentReader.read(FingerprintManager(listOf(OldFingerprint(2, "first"))))),
            ids(FingerprintEnrollmentReader.read(FingerprintManager(listOf(OldFingerprint(2, "second"))))))
    }

    @Test fun modernInvocationFailureCannotFallBackToOldIdentifier() {
        val entry = BrokenModernFingerprint()
        assertTrue(FingerprintEnrollmentReader.read(FingerprintManager(listOf(entry))) is EnrollmentSnapshot.Unavailable)
        assertFalse(entry.oldCalled)
    }

    @Test fun emptySuccessfulListIsAvailable() {
        assertEquals(emptySet<String>(), ids(FingerprintEnrollmentReader.read(FingerprintManager(emptyList()))))
    }

    @Test fun nullManagerListOrEntryAreUnavailable() {
        assertTrue(FingerprintEnrollmentReader.read(null) is EnrollmentSnapshot.Unavailable)
        assertTrue(FingerprintEnrollmentReader.read(FingerprintManager(null)) is EnrollmentSnapshot.Unavailable)
        assertTrue(FingerprintEnrollmentReader.read(FingerprintManager(listOf(null))) is EnrollmentSnapshot.Unavailable)
    }

    @Test fun missingKnownMethodsAreUnsupportedAndUnrelatedMethodsAreNotInvoked() {
        val unrelated = UnrelatedManager()
        assertSame(EnrollmentSnapshot.Unsupported, FingerprintEnrollmentReader.read(unrelated))
        assertFalse(unrelated.called)
        assertSame(EnrollmentSnapshot.Unsupported, FingerprintEnrollmentReader.read(FingerprintManager(listOf(Any()))))
    }

    @Test fun deniedEnumerationIsUnavailable() {
        assertTrue(FingerprintEnrollmentReader.read(DeniedManager()) is EnrollmentSnapshot.Unavailable)
    }

    @Test fun malformedSignatureIsNotAcceptedAsNumericIdentity() {
        assertTrue(FingerprintEnrollmentReader.read(FingerprintManager(listOf(WrongIdType()))) is EnrollmentSnapshot.Unavailable)
    }

    @Test fun duplicateIdentifiersAreNotSilentlyCollapsed() {
        assertTrue(FingerprintEnrollmentReader.read(FingerprintManager(listOf(OldFingerprint(1), OldFingerprint(1))))
            is EnrollmentSnapshot.Unavailable)
    }

    @Test fun groupAndDeviceSeparateOtherwiseEqualIds() {
        assertNotEquals(hardwareEnrollmentId(1, 2L, 3), hardwareEnrollmentId(2, 2L, 3))
        assertNotEquals(hardwareEnrollmentId(1, 2L, 3), hardwareEnrollmentId(1, 3L, 3))
    }

    @Test fun typedReaderRejectsPartialSnapshotsAndCopiesSuccess() {
        val entries = mutableListOf("one", "two")
        val snapshot = readHardwareEnrollments({ entries }) { it }
        entries.clear()
        assertEquals(setOf("one", "two"), ids(snapshot))
        assertTrue(readHardwareEnrollments({ listOf("one", "two") }) {
            if (it == "two") throw IllegalStateException("entry unreadable")
            it
        } is EnrollmentSnapshot.Unavailable)
    }

    @Test fun optionalTypedOemLinkageIsUnsupportedAndOtherLinkageIsUnavailable() {
        assertSame(EnrollmentSnapshot.Unsupported, readHardwareEnrollments<String>({ throw NoSuchMethodError() }) { it })
        assertSame(EnrollmentSnapshot.Unsupported, readHardwareEnrollments<String>({ throw NoClassDefFoundError() }) { it })
        assertTrue(readHardwareEnrollments<String>({ throw IncompatibleClassChangeError() }) { it } is EnrollmentSnapshot.Unavailable)
    }

    @Test fun fatalVmErrorsAreNotSwallowedThroughReflection() {
        assertThrows(OutOfMemoryError::class.java) { FingerprintEnrollmentReader.read(FatalManager()) }
    }

    @Test fun integerIdsPreserveLegacyIdentityAndNullIsUnavailable() {
        assertEquals(setOf("10", "20"), ids(readIntegerHardwareEnrollments { intArrayOf(20, 10) }))
        assertEquals(emptySet<String>(), ids(readIntegerHardwareEnrollments { intArrayOf() }))
        assertTrue(readIntegerHardwareEnrollments { null } is EnrollmentSnapshot.Unavailable)
        assertTrue(readIntegerHardwareEnrollments { intArrayOf(10, 10) } is EnrollmentSnapshot.Unavailable)
    }

    private fun ids(snapshot: EnrollmentSnapshot): Set<String> = (snapshot as EnrollmentSnapshot.Available).ids

    class FingerprintManager(private val entries: List<Any?>?) {
        var calls = 0
        var otherCalls = 0
        fun getEnrolledFingerprints(): List<Any?>? { calls++; return entries }
        fun getEnrolledFingerprints(userId: Int): List<Any?>? { otherCalls++; error("Wrong user overload: $userId") }
        fun getRegisteredTemplates(): List<Any?>? { otherCalls++; error("Unrelated getter") }
    }
    class OldFingerprint(private val id: Int, private val label: String = "finger") {
        fun getFingerId(): Int = id
        fun getGroupId(): Int = 7
        fun getDeviceId(): Long = 9L
        fun getName(): String = label
    }
    open class Identifier(private val id: Int) {
        fun getBiometricId(): Int = id
        fun getDeviceId(): Long = 9L
        fun getName(): String = "display name"
    }
    class ModernFingerprint(id: Int) : Identifier(id) { fun getGroupId(): Int = 7 }
    class RenamedFingerprint(id: Int) : Identifier(id) { fun getGroupId(): Int = 7 }
    class BrokenModernFingerprint {
        var oldCalled = false
        fun getBiometricId(): Int = throw SecurityException("blocked")
        fun getFingerId(): Int { oldCalled = true; return 1 }
        fun getGroupId(): Int = 7
        fun getDeviceId(): Long = 9L
    }
    class WrongIdType {
        fun getBiometricId(): String = "1"
        fun getGroupId(): Int = 7
        fun getDeviceId(): Long = 9L
    }
    class UnrelatedManager {
        var called = false
        fun getRegisteredTemplates(): List<String> { called = true; return listOf("guess") }
    }
    class DeniedManager { fun getEnrolledFingerprints(): List<Any> = throw SecurityException("denied") }
    class FatalManager { fun getEnrolledFingerprints(): List<Any> = throw OutOfMemoryError("fatal") }
}
