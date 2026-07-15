package dev.skomlach.biometric.compat.custom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareBiometricAssuranceTest {
    @Test
    fun onlyPassIsAcceptedAsAuthenticated() {
        assertTrue(SoftwareBiometricAssurance.PASS.isAcceptedAsAuthenticated())
        assertFalse(SoftwareBiometricAssurance.SPOOF.isAcceptedAsAuthenticated())
        assertFalse(SoftwareBiometricAssurance.MISMATCH.isAcceptedAsAuthenticated())
        assertFalse(SoftwareBiometricAssurance.UNAVAILABLE.isAcceptedAsAuthenticated())
        assertFalse(SoftwareBiometricAssurance.CAPTURE_UNTRUSTED.isAcceptedAsAuthenticated())
    }
}
