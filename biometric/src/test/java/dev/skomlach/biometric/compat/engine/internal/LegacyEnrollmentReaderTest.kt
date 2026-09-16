package dev.skomlach.biometric.compat.engine.internal

import org.junit.Assert.*
import org.junit.Test

class LegacyEnrollmentReaderTest {
    @Test fun plainIdsAndDuplicateEncodingRemainCompatible() {
        assertEquals(setOf("1; index=0", "1; index=1", "2"), ids(LegacyEnrollmentReader.read(setOf(IntManager(intArrayOf(1, 1, 2))))))
        assertEquals(setOf("face"), ids(LegacyEnrollmentReader.read(setOf(StringManager(listOf("face"))))))
    }

    @Test fun objectEncodingRetainsItsLegacyClassAndGetterNames() {
        assertEquals(setOf("Entry; getId=7;"), ids(LegacyEnrollmentReader.read(setOf(ObjectManager(listOf(Entry()))))))
    }

    @Test fun successfulEmptyEnumerationIsDistinctFromNoEnumerationContract() {
        assertEquals(emptySet<String>(), ids(LegacyEnrollmentReader.read(setOf(IntManager(intArrayOf())))))
        assertSame(EnrollmentSnapshot.Unsupported, LegacyEnrollmentReader.read(emptySet()))
        assertSame(EnrollmentSnapshot.Unsupported, LegacyEnrollmentReader.read(setOf(BooleanManager())))
    }

    @Test fun missingAndPartiallyReadableDataAreUnavailable() {
        assertTrue(LegacyEnrollmentReader.read(setOf(IntManager(null))) is EnrollmentSnapshot.Unavailable)
        assertTrue(LegacyEnrollmentReader.read(setOf(ObjectManager(listOf(Entry(), null)))) is EnrollmentSnapshot.Unavailable)
        val unknown = ObjectManager(listOf(Entry(), Any()))
        assertTrue(LegacyEnrollmentReader.read(setOf(unknown)) is EnrollmentSnapshot.Unavailable)
    }

    @Test fun oneManagerFailureCannotPublishAPartialMultiManagerSnapshot() {
        assertTrue(LegacyEnrollmentReader.read(linkedSetOf(IntManager(intArrayOf(1)), BrokenManager())) is EnrollmentSnapshot.Unavailable)
    }

    @Test fun getterFailureCannotPublishEarlierIds() {
        assertTrue(LegacyEnrollmentReader.read(setOf(ObjectManager(listOf(Entry(), BrokenEntry())))) is EnrollmentSnapshot.Unavailable)
    }

    @Test fun linkageFailuresAreUnavailable() {
        assertTrue(LegacyEnrollmentReader.read(setOf(MissingApiManager())) is EnrollmentSnapshot.Unavailable)
    }

    @Test fun fatalErrorsAreNotSwallowed() {
        assertThrows(OutOfMemoryError::class.java) { LegacyEnrollmentReader.read(setOf(FatalManager())) }
    }

    private fun ids(snapshot: EnrollmentSnapshot): Set<String> = (snapshot as EnrollmentSnapshot.Available).ids
    class IntManager(private val values: IntArray?) { fun getEnrolledTemplates(): IntArray? = values }
    class StringManager(private val values: List<String>) { fun getRegisteredTemplates(): List<String> = values }
    class ObjectManager(private val values: List<Any?>) { fun getEnrolledTemplates(): List<Any?> = values }
    class BooleanManager { fun hasEnrolledTemplates(): Boolean = true }
    class Entry { fun getId(): Int = 7 }
    class BrokenEntry { fun getId(): Int = throw SecurityException("ID blocked") }
    class BrokenManager { fun getEnrolledTemplates(): IntArray = throw SecurityException("Enumeration blocked") }
    class MissingApiManager { fun getEnrolledTemplates(): IntArray = throw NoSuchMethodError() }
    class FatalManager { fun getEnrolledTemplates(): IntArray = throw OutOfMemoryError() }
}
