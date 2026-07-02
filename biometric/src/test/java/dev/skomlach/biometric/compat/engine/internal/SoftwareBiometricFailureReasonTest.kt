package dev.skomlach.biometric.compat.engine.internal

import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import org.junit.Assert.assertEquals
import org.junit.Test

class SoftwareBiometricFailureReasonTest {

    @Test
    fun `keeps authentication failed when manager is not locked out`() {
        val resolved = resolveSoftwareFailureReason(
            AuthenticationFailureReason.AUTHENTICATION_FAILED,
            null
        )

        assertEquals(AuthenticationFailureReason.AUTHENTICATION_FAILED, resolved)
    }

    @Test
    fun `upgrades authentication failed to temporary lockout only when manager reports lockout`() {
        val resolved = resolveSoftwareFailureReason(
            AuthenticationFailureReason.AUTHENTICATION_FAILED,
            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT
        )

        assertEquals(AuthenticationFailureReason.LOCKED_OUT, resolved)
    }

    @Test
    fun `upgrades sensor failure to permanent unavailability only when manager reports permanent lockout`() {
        val resolved = resolveSoftwareFailureReason(
            AuthenticationFailureReason.SENSOR_FAILED,
            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        )

        assertEquals(AuthenticationFailureReason.HARDWARE_UNAVAILABLE, resolved)
    }
}
