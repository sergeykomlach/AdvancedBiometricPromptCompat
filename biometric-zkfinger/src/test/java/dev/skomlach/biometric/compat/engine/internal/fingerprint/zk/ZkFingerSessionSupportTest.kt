package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZkFingerSessionSupportTest {

    @Test
    fun `sanitizeZkEnrollmentTag rejects blank input`() {
        assertNull(sanitizeZkEnrollmentTag(null))
        assertNull(sanitizeZkEnrollmentTag("   "))
    }

    @Test
    fun `sanitizeZkEnrollmentTag normalizes illegal characters`() {
        assertEquals(
            "zkfinger_bad_tag_name",
            sanitizeZkEnrollmentTag("  zkfinger/bad:tag name  ")
        )
    }

    @Test
    fun `resolveZkPermissionAction is stable for package name`() {
        assertEquals(
            "dev.skomlach.sample.dev.skomlach.biometric.zkfinger.USB_PERMISSION",
            resolveZkPermissionAction("dev.skomlach.sample")
        )
    }

    @Test
    fun `resolveZkPermissionRequestCode is deterministic`() {
        assertEquals(
            544897,
            resolveZkPermissionRequestCode(
                vendorId = 6997,
                productId = 41,
                deviceIndex = 3
            )
        )
        assertEquals(
            resolveZkPermissionRequestCode(
                vendorId = 6997,
                productId = 41,
                deviceIndex = 3
            ),
            resolveZkPermissionRequestCode(
                vendorId = 6997,
                productId = 41,
                deviceIndex = 3
            )
        )
    }
}
