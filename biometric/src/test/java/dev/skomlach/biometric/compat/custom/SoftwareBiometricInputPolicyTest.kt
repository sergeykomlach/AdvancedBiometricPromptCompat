package dev.skomlach.biometric.compat.custom

import org.junit.Assert.assertEquals
import org.junit.Test

class SoftwareBiometricInputPolicyTest {

    @Test
    fun acceptsFiniteValuesWithinConfiguredBounds() {
        assertEquals(
            SoftwareBiometricInputDecision.ACCEPT,
            SoftwareBiometricInputPolicy.validateFinite(0.5, -1.0, 1.0)
        )
    }

    @Test
    fun rejectsNonFiniteAndOutOfRangeValues() {
        assertEquals(
            SoftwareBiometricInputDecision.REJECT_INVALID_VALUE,
            SoftwareBiometricInputPolicy.validateFinite(Double.NaN, -1.0, 1.0)
        )
        assertEquals(
            SoftwareBiometricInputDecision.REJECT_INVALID_VALUE,
            SoftwareBiometricInputPolicy.validateFinite(2.0, -1.0, 1.0)
        )
    }

    @Test
    fun rejectsOversizedPayloadsAndCollections() {
        assertEquals(
            SoftwareBiometricInputDecision.REJECT_TOO_LARGE,
            SoftwareBiometricInputPolicy.validateSize(65_537, 65_536)
        )
        assertEquals(
            SoftwareBiometricInputDecision.REJECT_TOO_LARGE,
            SoftwareBiometricInputPolicy.validateSize(101, 100)
        )
    }

    @Test
    fun rejectsClockRollbackAndExpiredCapture() {
        assertEquals(
            SoftwareBiometricInputDecision.REJECT_INVALID_TIME,
            SoftwareBiometricInputPolicy.validateDuration(500L, 400L, 1_000L)
        )
        assertEquals(
            SoftwareBiometricInputDecision.REJECT_EXPIRED,
            SoftwareBiometricInputPolicy.validateDuration(0L, 1_001L, 1_000L)
        )
    }
}
