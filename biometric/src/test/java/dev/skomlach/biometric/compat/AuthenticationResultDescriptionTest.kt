package dev.skomlach.biometric.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthenticationResultDescriptionTest {

    @Test
    fun withMissingPermissionDescriptionAddsDescriptionOnlyForMissingPermission() {
        val result = AuthenticationResult(
            type = BiometricType.BIOMETRIC_FACE,
            reason = AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR
        )

        assertEquals(
            "Camera permission is required to use this feature.",
            result.withMissingPermissionDescription("Camera permission is required to use this feature.").description
        )
    }

    @Test
    fun withMissingPermissionDescriptionPreservesExistingDescription() {
        val result = AuthenticationResult(
            type = BiometricType.BIOMETRIC_FACE,
            reason = AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR,
            description = "Existing message"
        )

        assertEquals(
            "Existing message",
            result.withMissingPermissionDescription("Camera permission is required to use this feature.").description
        )
    }

    @Test
    fun withMissingPermissionDescriptionDoesNotTouchOtherReasons() {
        val result = AuthenticationResult(
            type = BiometricType.BIOMETRIC_FACE,
            reason = AuthenticationFailureReason.NO_HARDWARE
        )

        assertNull(result.withMissingPermissionDescription("Camera permission is required to use this feature.").description)
    }
}
