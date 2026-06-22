package dev.skomlach.common.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataProvidersSecurityTest {
    @Test
    fun extractFileNameFromUrlRemovesPathTraversalSegments() {
        val fileName = DataProviders.extractFileNameFromUrl(
            "https://example.com/devices.json?filename=..%2F..%2Fsecret.json"
        )

        assertFalse(fileName.contains("/"))
        assertFalse(fileName.contains("\\"))
        assertFalse(fileName.startsWith("."))
        assertTrue(fileName.endsWith("secret.json"))
    }

    @Test
    fun sanitizeCacheFileNameFallsBackForEmptyUnsafeName() {
        val fileName = DataProviders.sanitizeCacheFileName("../", "https://example.com/data")

        assertTrue(fileName.startsWith("cache_"))
        assertTrue(fileName.endsWith(".json"))
    }
}
