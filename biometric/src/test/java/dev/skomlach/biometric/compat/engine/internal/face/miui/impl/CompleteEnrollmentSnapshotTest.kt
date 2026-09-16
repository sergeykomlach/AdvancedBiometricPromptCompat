package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

import org.junit.Assert.*
import org.junit.Test

class CompleteEnrollmentSnapshotTest {
    @Test fun missingAndLoadingAreUnavailable() {
        val snapshot = CompleteEnrollmentSnapshot<String>()
        assertNull(snapshot.read(true))
        snapshot.publish(listOf("face"))
        assertNull(snapshot.read(false))
        assertEquals(listOf("face"), snapshot.read(true))
    }
    @Test fun onlyACompleteEmptyResponseMeansEmpty() {
        val snapshot = CompleteEnrollmentSnapshot<String>()
        snapshot.publish(emptyList())
        assertEquals(emptyList<String>(), snapshot.read(true))
        snapshot.publish(null)
        assertNull(snapshot.read(true))
    }
    @Test fun partialResponsesDoNotExposePreviouslyPublishedOrPartialIds() {
        val snapshot = CompleteEnrollmentSnapshot<String>()
        snapshot.publish(listOf("old"))
        snapshot.publish(listOf("new", null))
        assertNull(snapshot.read(true))
        snapshot.publish(listOf("new"))
        snapshot.invalidate()
        assertNull(snapshot.read(true))
    }
    @Test fun callerMutationsCannotChangePublishedMembership() {
        val input = mutableListOf("face")
        val snapshot = CompleteEnrollmentSnapshot<String>()
        snapshot.publish(input)
        input.clear()
        assertEquals(listOf("face"), snapshot.read(true))
    }
}
