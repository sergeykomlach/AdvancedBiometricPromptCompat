package dev.skomlach.biometric.compat

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPermissionPolicyTest {

    @Test
    fun setupStopsAfterPermissionDeniedWhenNoRouteRemains() {
        assertTrue(
            shouldStopAfterPermissionDenied(
                enroll = true,
                deniedPermissions = listOf(Manifest.permission.RECORD_AUDIO),
                hasUsableRouteAfterDeniedModules = false
            )
        )
    }

    @Test
    fun setupContinuesAfterPermissionDeniedWhenAnotherRouteRemains() {
        assertFalse(
            shouldStopAfterPermissionDenied(
                enroll = true,
                deniedPermissions = listOf(Manifest.permission.RECORD_AUDIO),
                hasUsableRouteAfterDeniedModules = true
            )
        )
    }

    @Test
    fun authenticationDoesNotStopAfterPermissionDeniedPolicy() {
        assertFalse(
            shouldStopAfterPermissionDenied(
                enroll = false,
                deniedPermissions = listOf(Manifest.permission.RECORD_AUDIO)
            )
        )
    }

    @Test
    fun setupDoesNotStopWhenNoPermissionWasDenied() {
        assertFalse(
            shouldStopAfterPermissionDenied(
                enroll = true,
                deniedPermissions = emptyList()
            )
        )
    }
}
