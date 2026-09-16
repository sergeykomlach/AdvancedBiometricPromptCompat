package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import dev.skomlach.biometric.compat.custom.SoftwareBiometricEnrollment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TensorFlowFaceStateSupportTest {
    @Test fun storedMembershipDoesNotRequirePermissionOrADetector() {
        val json = """{"face2":{"id":"2"},"face1":{"id":"1"}}"""
        assertFalse(hasUsableFaceEnrollment(json, false))
        assertEquals(setOf("face1", "face2"), readStoredFaceEnrollmentIds(json))
    }

    @Test fun missingAndEmptyObjectsAreCompleteEmptySnapshots() {
        assertEquals(emptySet<String>(), readStoredFaceEnrollmentIds(null))
        assertEquals(emptySet<String>(), readStoredFaceEnrollmentIds("{}"))
    }

    @Test fun malformedStoredEnrollmentIsUnavailableRatherThanEmpty() {
        for (json in listOf("", "{broken", "[]", "null")) {
            val snapshot = SoftwareBiometricEnrollment.read { readStoredFaceEnrollmentIds(json) }
            assertTrue(snapshot is SoftwareBiometricEnrollment.Unavailable)
        }
    }

    @Test fun corruptEntryCannotProduceAPartialSnapshot() {
        for (json in listOf("""{"face1":{},"face2":null}""", """{"face1":{},"face2":3}""")) {
            val snapshot = SoftwareBiometricEnrollment.read { readStoredFaceEnrollmentIds(json) }
            assertTrue(snapshot is SoftwareBiometricEnrollment.Unavailable)
        }
    }

    @Test fun revokedPermissionHidesEnrollmentWithoutDeletingTemplates() {
        val templates = """{"face1":{"id":"1"}}"""
        assertTrue(hasUsableFaceEnrollment(templates, true))
        assertFalse(hasUsableFaceEnrollment(templates, false))
        assertTrue(hasRegisteredTemplates(templates))
        assertTrue(hasUsableFaceEnrollment(templates, true))
        assertFalse(hasUsableFaceEnrollment("{}", true))
    }


    @Test
    fun countRegisteredTemplatesReturnsZeroForMissingOrEmptyJson() {
        assertEquals(0, countRegisteredTemplates(null))
        assertEquals(0, countRegisteredTemplates(""))
        assertEquals(0, countRegisteredTemplates("{}"))
        assertFalse(hasRegisteredTemplates("{}"))
    }

    @Test
    fun countRegisteredTemplatesCountsTopLevelTemplateEntries() {
        val json = """
            {
              "face1": {"id":"1"},
              "face2": {"id":"2"}
            }
        """.trimIndent()

        assertEquals(2, countRegisteredTemplates(json))
        assertTrue(hasRegisteredTemplates(json))
    }

    @Test
    fun countRegisteredTemplatesRejectsMalformedJson() {
        assertEquals(0, countRegisteredTemplates("{broken"))
        assertFalse(hasRegisteredTemplates("{broken"))
    }
}
