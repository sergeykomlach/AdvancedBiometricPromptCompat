package dev.skomlach.biometric.compat

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPermissionPolicyTest {

    @Test
    fun setupStopsAfterCameraPermissionDenied() {
        assertTrue(
            shouldStopAfterPermissionDenied(
                enroll = true,
                deniedPermissions = listOf(Manifest.permission.CAMERA)
            )
        )
    }

    @Test
    fun authenticationDoesNotStopAfterCameraPermissionDeniedPolicy() {
        assertFalse(
            shouldStopAfterPermissionDenied(
                enroll = false,
                deniedPermissions = listOf(Manifest.permission.CAMERA)
            )
        )
    }

    @Test
    fun setupDoesNotStopForUnrelatedPermission() {
        assertFalse(
            shouldStopAfterPermissionDenied(
                enroll = true,
                deniedPermissions = listOf(Manifest.permission.POST_NOTIFICATIONS)
            )
        )
    }
}
