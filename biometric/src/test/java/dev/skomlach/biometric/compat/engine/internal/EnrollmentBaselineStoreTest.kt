package dev.skomlach.biometric.compat.engine.internal

import org.junit.Assert.*
import org.junit.Test

class EnrollmentBaselineStoreTest {
    @Test fun failedCommitCannotBeTrustedByARecreatedTracker() {
        var memory: Set<String>? = null
        var fail = true
        val store = EnrollmentBaselineStore({ memory }, { memory = it; !fail })
        fun tracker() = EnrollmentChangeTracker(
            { EnrollmentSnapshot.Available(listOf("finger")) }, store::read, { null }, store::write, {}
        )
        assertEquals(EnrollmentChange.UNAVAILABLE, tracker().check())
        assertNotNull(memory)
        val recreated = tracker()
        assertEquals(EnrollmentChange.UNAVAILABLE, recreated.check())
        fail = false
        assertEquals(EnrollmentChange.UNCHANGED, recreated.acknowledge())
        assertEquals(EnrollmentChange.UNCHANGED, tracker().check())
    }

    @Test fun thrownCommitAlsoBlocksUnpersistedReads() {
        val store = EnrollmentBaselineStore({ setOf("in-memory") }, { throw IllegalStateException("disk unavailable") })
        assertThrows(IllegalStateException::class.java) { store.write(setOf("new")) }
        assertThrows(IllegalStateException::class.java) { store.read() }
    }

    @Test fun readerCannotMutateStoredSet() {
        val memory = mutableSetOf("old")
        val store = EnrollmentBaselineStore({ memory }, { true })
        val value = store.read()
        memory.add("new")
        assertEquals(setOf("old"), value)
    }

    @Test fun enumerationFailureDoesNotReadOrWriteBaselines() {
        for (snapshot in listOf(EnrollmentSnapshot.Unsupported, EnrollmentSnapshot.Unavailable())) {
            val tracker = EnrollmentChangeTracker({ snapshot }, { error("read") }, { error("legacy") }, { error("write") }, { throw it })
            val expected = if (snapshot === EnrollmentSnapshot.Unsupported) EnrollmentChange.UNSUPPORTED else EnrollmentChange.UNAVAILABLE
            assertEquals(expected, tracker.check())
            assertEquals(expected, tracker.acknowledge())
        }
    }
}
