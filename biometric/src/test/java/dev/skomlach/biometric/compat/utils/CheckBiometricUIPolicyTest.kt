package dev.skomlach.biometric.compat.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckBiometricUIPolicyTest {
    @Test fun validatedPromptLayoutConfirmsUiResources() {
        assertEquals(BiometricUiAvailability.AVAILABLE, resolveBiometricUiAvailability(true, true))
    }

    @Test fun disabledProviderWinsOverCachedResources() {
        assertEquals(BiometricUiAvailability.UNAVAILABLE, resolveBiometricUiAvailability(false, true))
    }

    @Test fun unreadableOrUnrecognizedXmlDoesNotMeanMissingUi() {
        assertEquals(BiometricUiAvailability.UNKNOWN, resolveBiometricUiAvailability(true, false))
    }

    @Test fun packageVisibilityFailureDoesNotMeanMissingUi() {
        assertEquals(BiometricUiAvailability.UNKNOWN, resolveBiometricUiAvailability(null, false))
        assertEquals(BiometricUiAvailability.AVAILABLE, resolveBiometricUiAvailability(null, true))
    }
}
