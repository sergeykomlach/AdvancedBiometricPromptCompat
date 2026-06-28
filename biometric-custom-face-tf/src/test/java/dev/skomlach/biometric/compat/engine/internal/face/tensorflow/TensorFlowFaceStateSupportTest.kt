package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TensorFlowFaceStateSupportTest {

    @Test
    fun countRegisteredTemplatesReturnsZeroForMissingOrEmptyJson() {
        assertEquals(0, countRegisteredTemplates(null))
        assertEquals(0, countRegisteredTemplates(""))
        assertEquals(0, countRegisteredTemplates("{}"))
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
