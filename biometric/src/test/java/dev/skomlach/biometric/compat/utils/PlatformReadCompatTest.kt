package dev.skomlach.biometric.compat.utils

import org.junit.Assert.*
import org.junit.Test

class PlatformReadCompatTest {
    @Test
    fun supportedPlatformReturnsExactDelegateWithoutAccessingHiddenFields() {
        val originalDelegate = Any()
        var legacyReads = 0
        val delegate = readPlatformOrLegacy(true, { originalDelegate }, { legacyReads++; Any() })
        assertSame(originalDelegate, delegate)
        assertEquals(0, legacyReads)
    }

    @Test
    fun nullPlatformDelegateIsAuthoritative() {
        var legacyReads = 0
        val delegate = readPlatformOrLegacy<Any?>(true, { null }, { legacyReads++; Any() })
        assertNull(delegate)
        assertEquals(0, legacyReads)
    }

    @Test
    fun emptyPlatformWindowSnapshotDoesNotTriggerLegacyDiscovery() {
        var legacyReads = 0
        val roots = readPlatformOrLegacy(true, { emptyList<String>() }, { legacyReads++; listOf("stale") })
        assertTrue(roots.isEmpty())
        assertEquals(0, legacyReads)
    }

    @Test
    fun oldPlatformUsesLegacyWithoutResolvingNewApi() {
        val roots = listOf("activity", "dialog", "popup")
        var legacyReads = 0
        val result = readPlatformOrLegacy(false,
            { throw AssertionError("New API must not be resolved on old Android") },
            { legacyReads++; roots }
        )
        assertSame(roots, result)
        assertEquals(1, legacyReads)
    }

    @Test
    fun missingFrameworkMethodReportsAndUsesLegacy() {
        val failure = NoSuchMethodError("vendor framework")
        val failures = mutableListOf<LinkageError>()
        val delegate = Any()
        val result = readPlatformOrLegacy(true, { throw failure }, { delegate }, { failures += it })
        assertSame(delegate, result)
        assertEquals(listOf(failure), failures)
    }

    @Test
    fun missingFrameworkClassReportsAndUsesLegacy() {
        val failure = NoClassDefFoundError("vendor framework")
        val failures = mutableListOf<LinkageError>()
        val roots = listOf("activity", "dialog")
        val result = readPlatformOrLegacy(true, { throw failure }, { roots }, { failures += it })
        assertSame(roots, result)
        assertEquals(listOf(failure), failures)
    }

    @Test
    fun unrelatedPlatformFailureIsNotDisguisedAsLegacySuccess() {
        val failure = IllegalStateException("invalid state")
        var legacyReads = 0
        val result = assertThrows(IllegalStateException::class.java) {
            readPlatformOrLegacy(true, { throw failure }, { legacyReads++; "legacy" })
        }
        assertSame(failure, result)
        assertEquals(0, legacyReads)
    }

    @Test
    fun fallbackFailureRemainsVisible() {
        val failure = SecurityException("legacy access denied")
        val result = assertThrows(SecurityException::class.java) {
            readPlatformOrLegacy<Any>(true,
                { throw NoSuchMethodError("vendor framework") },
                { throw failure }
            )
        }
        assertSame(failure, result)
    }
}
