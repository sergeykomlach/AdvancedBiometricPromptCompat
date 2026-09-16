package dev.skomlach.biometric.compat.utils

import org.junit.Assert.*
import org.junit.Test

class BiometricCapabilityCacheTest {
    private val preferences = mutableMapOf<String, String>()
    private var writes = 0
    private fun cache() = BiometricCapabilityCache(preferences::get) { key, value ->
        writes++
        preferences[key] = value
    }

    @Test fun resultSurvivesCreatingANewCacheInstance() {
        cache().put("sensor", listOf("build", "config", "under display"), "UNDER_DISPLAY")
        assertEquals("UNDER_DISPLAY", cache().get("sensor", listOf("build", "config", "under display")))
    }

    @Test fun firmwareConfigurationAndMetadataChangesInvalidateResults() {
        val input = listOf("build", "config", "metadata")
        cache().put("sensor", input, "SIDE")
        for (i in input.indices) {
            val changed = input.toMutableList().apply { this[i] += "-new" }
            assertNull(cache().get("sensor", changed))
        }
    }

    @Test fun providerUpdateAndEnableChangesInvalidateMissingUi() {
        val input = listOf("build", "provider", "revision", "false", "false")
        cache().put("ui", input, "true")
        for (i in 1..4) {
            assertNull(cache().get("ui", input.toMutableList().apply { this[i] += "-changed" }))
        }
    }

    @Test fun vendorsDoNotEvictEachOtherAndCaseModeHasItsOwnSlot() {
        cache().put("vendor-LG-false", listOf("build"), "false")
        cache().put("vendor-OnePlus-true", listOf("build"), "true")
        assertEquals("false", cache().get("vendor-LG-false", listOf("build")))
        assertEquals("true", cache().get("vendor-OnePlus-true", listOf("build")))
        assertNull(cache().get("vendor-LG-true", listOf("build")))
    }

    @Test fun updatesReplaceOneSlotAndIdenticalValuesDoNotWriteAgain() {
        cache().put("sensor", listOf("before"), "SIDE")
        cache().put("sensor", listOf("before"), "SIDE")
        assertEquals(1, writes)
        cache().put("sensor", listOf("after"), "UNDER_DISPLAY")
        assertEquals(1, preferences.size)
        assertEquals(2, writes)
    }

    @Test fun oldGuessesAndCorruptedRecordsAreIgnored() {
        preferences["hasUnderDisplayFingerprint-build"] = "true"
        assertNull(cache().get("hasUnderDisplayFingerprint", listOf("build")))
        preferences["hasUnderDisplayFingerprint-evidence-v3"] = "malformed"
        assertNull(cache().get("hasUnderDisplayFingerprint", listOf("build")))
    }

    @Test fun inputBoundariesDoNotCollide() {
        cache().put("sensor", listOf("a|b", "c"), "SIDE")
        assertNull(cache().get("sensor", listOf("a", "b|c")))
    }
}
