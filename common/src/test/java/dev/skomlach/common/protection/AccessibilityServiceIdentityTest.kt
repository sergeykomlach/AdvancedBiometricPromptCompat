package dev.skomlach.common.protection

import org.junit.Assert.*
import org.junit.Test

class AccessibilityServiceIdentityTest {
    private val trusted = AccessibilityServiceIdentity.from("trusted.pkg", "trusted.pkg.Reader")!!

    @Test fun fullAndRelativeClassesIdentifyTheSameService() {
        assertEquals(trusted, AccessibilityServiceIdentity.from("trusted.pkg", ".Reader"))
        assertEquals("trusted.pkg.Reader", trusted.className)
    }

    @Test fun differentPackagesClassesAndCaseDoNotMatch() {
        assertNotEquals(trusted, AccessibilityServiceIdentity.from("other.pkg", "trusted.pkg.Reader"))
        assertNotEquals(trusted, AccessibilityServiceIdentity.from("trusted.pkg", ".ReaderExtra"))
        assertNotEquals(trusted, AccessibilityServiceIdentity.from("trusted.pkg", ".reader"))
        assertNotEquals(trusted, AccessibilityServiceIdentity.from("Trusted.pkg", ".Reader"))
    }

    @Test fun variantPackageDoesNotRewriteFullyQualifiedClass() {
        val alpha = AccessibilityServiceIdentity.from("trusted.pkg.alpha", "trusted.pkg.Reader")!!
        assertEquals("trusted.pkg.Reader", alpha.className)
        assertNotEquals(alpha, AccessibilityServiceIdentity.from("trusted.pkg.alpha", ".Reader"))
    }

    @Test fun malformedFieldsAreNotIdentities() {
        for (invalid in listOf(null, "", " ", "pkg/name", "pkg:name", "pkg name")) {
            assertNull(AccessibilityServiceIdentity.from(invalid, "trusted.pkg.Reader"))
            assertNull(AccessibilityServiceIdentity.from("trusted.pkg", invalid))
        }
        assertNull(AccessibilityServiceIdentity.from("trusted.pkg", "."))
    }

    @Test fun emptyEnabledListRemainsTrusted() {
        assertTrue(areAllAccessibilityServicesTrusted(emptyList()) { error("No services") })
    }

    @Test fun everyEnabledServiceMustBeTrusted() {
        val untrusted = AccessibilityServiceIdentity.from("other.pkg", ".Service")!!
        assertTrue(areAllAccessibilityServicesTrusted(listOf(trusted, trusted)) { it == trusted })
        assertFalse(areAllAccessibilityServicesTrusted(listOf(trusted, untrusted)) { it == trusted })
        assertFalse(areAllAccessibilityServicesTrusted(listOf(untrusted, trusted)) { it == trusted })
    }

    @Test fun malformedEnabledServiceFailsClosedEvenAmongTrustedServices() {
        assertFalse(areAllAccessibilityServicesTrusted(listOf(null)) { error("Invalid identity") })
        assertFalse(areAllAccessibilityServicesTrusted(listOf(trusted, null)) { true })
        assertFalse(areAllAccessibilityServicesTrusted(listOf(null, trusted)) { true })
    }
}
