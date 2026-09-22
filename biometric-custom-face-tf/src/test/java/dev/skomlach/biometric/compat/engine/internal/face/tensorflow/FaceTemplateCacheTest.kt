package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.*
import org.junit.Test

class FaceTemplateCacheTest {
    @Test fun anotherInstanceRemovingOneProfileInvalidatesTheFirstInstancesSnapshot() {
        var payload: String? = "X,Y"
        val first = FaceTemplateCache({ payload }, ::decode)
        val second = FaceTemplateCache({ payload }, ::decode)
        assertEquals(setOf("X", "Y"), first.snapshot())
        assertEquals(setOf("X", "Y"), second.snapshot())
        payload = "Y"
        assertEquals(setOf("Y"), second.snapshot())
        assertEquals(setOf("Y"), first.snapshot())
    }

    @Test fun replacingDataUnderTheSameProfileNameIsVisible() {
        var payload: String? = "X:old"
        val cache = FaceTemplateCache({ payload }) { it }
        assertEquals("X:old", cache.snapshot())
        payload = "X:new"
        assertEquals("X:new", cache.snapshot())
        payload = null
        assertNull(cache.snapshot())
    }

    @Test fun unchangedPayloadIsNotDecodedForEveryCameraFrame() {
        var decodes = 0
        val cache = FaceTemplateCache({ "X" }) { decodes++; decode(it) }
        assertEquals(setOf("X"), cache.snapshot())
        assertEquals(setOf("X"), cache.snapshot())
        assertEquals(1, decodes)
    }

    @Test fun unreadableStorageCannotReusePreviouslyAcceptedTemplates() {
        var readable = true
        val cache = FaceTemplateCache({ check(readable) { "storage unavailable" }; "X" }, ::decode)
        assertEquals(setOf("X"), cache.snapshot())
        readable = false
        try {
            cache.snapshot()
            fail("unreadable storage returned a cached profile")
        } catch (expected: IllegalStateException) {
            assertEquals("storage unavailable", expected.message)
        }
    }

    private fun decode(payload: String?): Set<String> =
        payload?.split(',')?.toSet().orEmpty()
}
