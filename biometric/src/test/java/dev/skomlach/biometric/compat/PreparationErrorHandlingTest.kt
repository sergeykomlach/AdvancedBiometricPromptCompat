package dev.skomlach.biometric.compat

import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparationErrorHandlingTest {

    @Test
    fun noPermissionsPreparationErrorIsSkippable() {
        assertTrue(
            isSkippablePreparationError(
                AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_NO_PERMISSIONS
            )
        )
    }

    @Test
    fun noHardwarePreparationErrorIsSkippable() {
        assertTrue(
            isSkippablePreparationError(
                AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT
            )
        )
    }

    @Test
    fun hardwareUnavailablePreparationErrorIsSkippable() {
        assertTrue(
            isSkippablePreparationError(
                AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE
            )
        )
    }

    @Test
    fun authenticationFailedPreparationErrorIsTerminal() {
        assertFalse(
            isSkippablePreparationError(
                AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS
            )
        )
    }
}
