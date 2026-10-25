package dev.skomlach.common.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GsmarenaSensorSourcePolicyTest {
    private fun record(source: String?, fields: Map<String, String>) = DeviceSpec(
        "Phone", fields, source?.let { mapOf("spec_source" to it) }.orEmpty())

    @Test fun legacyRecordsRetainTheirExistingPrecedence() {
        assertTrue(record(null, emptyMap()).hasUsableSensorSource())
        assertTrue(record(null, mapOf("Sensors" to "Fingerprint (rear-mounted)")).hasUsableSensorSource())
    }

    @Test fun partialInlineRecordCannotHideAFullFallback() {
        assertFalse(record("review_inline", emptyMap()).hasUsableSensorSource())
        assertFalse(record("review_inline", mapOf("Misc" to "Fingerprint reader")).hasUsableSensorSource())
        assertFalse(record("review_inline", mapOf("Feature 1" to "No fingerprint sensor on US model")).hasUsableSensorSource())
    }

    @Test fun unusableInlineSensorValuesKeepFallbackAvailable() {
        for (value in listOf("", "  ", "Not found", " , Not found, ")) {
            assertFalse(record("review_inline", mapOf("Sensors" to value)).hasUsableSensorSource())
        }
    }

    @Test fun explicitInlineSensorsRemainUsable() {
        assertTrue(record("review_inline", mapOf("Sensors" to "Fingerprint (rear-mounted), accelerometer")).hasUsableSensorSource())
        assertTrue(record("review_inline", mapOf("sensors" to "Iris scanner")).hasUsableSensorSource())
    }
}
