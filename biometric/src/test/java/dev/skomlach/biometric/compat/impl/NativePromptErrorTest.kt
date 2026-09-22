package dev.skomlach.biometric.compat.impl

import androidx.biometric.BiometricPrompt
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.*
import org.junit.Test

class NativePromptErrorTest {
    @Test fun oneNativeProcessingFailureDoesNotInventALockout() {
        for (reason in listOf(AuthenticationFailureReason.AUTHENTICATION_FAILED, AuthenticationFailureReason.SENSOR_FAILED)) {
            assertFalse(shouldApplyExhaustedLegacyLockout(true, reason))
            assertTrue(shouldApplyExhaustedLegacyLockout(false, reason))
        }
        assertFalse(shouldApplyExhaustedLegacyLockout(false, AuthenticationFailureReason.UNKNOWN))
    }
    @Test fun terminalSensorErrorsAreClassifiedWithoutRestartingADeadPrompt() {
        assertEquals(AuthenticationFailureReason.AUTHENTICATION_FAILED,
            nativePromptFailureReason(BiometricPrompt.ERROR_UNABLE_TO_PROCESS))
        assertEquals(AuthenticationFailureReason.SENSOR_FAILED,
            nativePromptFailureReason(BiometricPrompt.ERROR_NO_SPACE))
    }

    @Test fun vendorAndUnknownErrorsTerminateOnlyTheNativeBranchForAny() {
        for (code in listOf(BiometricPrompt.ERROR_VENDOR, 999, 1999)) {
            assertEquals(AuthenticationFailureReason.UNKNOWN, nativePromptFailureReason(code))
            val finger = BiometricType.BIOMETRIC_FINGERPRINT
            val voice = BiometricType.BIOMETRIC_VOICE
            val results = mutableMapOf(finger to AuthResult(AuthResult.AuthResultState.FATAL_ERROR, null))
            assertEquals(AuthenticationCompletion.PENDING, resolveApi28Completion(
                BiometricConfirmation.ANY, listOf(finger, voice), emptyList(),
                AuthResult.AuthResultState.FATAL_ERROR, results))
            results[voice] = AuthResult(AuthResult.AuthResultState.SUCCESS, null)
            assertEquals(AuthenticationCompletion.SUCCEEDED, resolveApi28Completion(
                BiometricConfirmation.ANY, listOf(finger, voice), emptyList(),
                AuthResult.AuthResultState.FATAL_ERROR, results))
        }
    }

    @Test fun explicitCancellationRemainsSessionWide() {
        assertEquals(AuthenticationFailureReason.CANCELED, nativePromptFailureReason(BiometricPrompt.ERROR_CANCELED))
        assertEquals(AuthenticationFailureReason.CANCELED_BY_USER, nativePromptFailureReason(BiometricPrompt.ERROR_USER_CANCELED))
        assertEquals(AuthenticationFailureReason.CANCELED_BY_USER, nativePromptFailureReason(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
    }
}
