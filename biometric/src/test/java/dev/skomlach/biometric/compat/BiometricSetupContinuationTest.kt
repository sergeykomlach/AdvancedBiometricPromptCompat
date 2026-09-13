package dev.skomlach.biometric.compat

import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricSetupContinuationTest {
    @Test fun pocoAndFoldConfirmExistingHardwareThroughAuthentication() {
        assertEquals(BiometricSetupContinuation.CONFIRM_HARDWARE,
            resolveBiometricSetupContinuation(false, false, false))
    }

    @Test fun newSystemEnrollmentCompletesHardwareOnlySetup() {
        assertEquals(BiometricSetupContinuation.COMPLETE_SYSTEM_ENROLLMENT,
            resolveBiometricSetupContinuation(false, false, true))
    }

    @Test fun softwareOnlySetupEnrollsAgainOnEveryInvocation() {
        repeat(2) {
            assertEquals(BiometricSetupContinuation.ENROLL_SOFTWARE,
                resolveBiometricSetupContinuation(false, true, false))
        }
    }

    @Test fun pixel7aEnrolledHardwareDoesNotRequireAnotherConfirmation() {
        assertEquals(BiometricSetupContinuation.ENROLL_SOFTWARE,
            resolveBiometricSetupContinuation(false, true, false))
    }

    @Test fun returningFromHardwareEnrollmentStartsSoftwareEnrollment() {
        assertEquals(BiometricSetupContinuation.ENROLL_SOFTWARE,
            resolveBiometricSetupContinuation(false, true, true))
    }

    @Test fun cancelingRequiredHardwareEnrollmentCannotStartSoftwareEnrollment() {
        for (softwareAvailable in listOf(false, true)) {
            assertEquals(BiometricSetupContinuation.CANCELED,
                resolveBiometricSetupContinuation(true, softwareAvailable, false))
        }
    }

    @Test fun systemEnrollmentAloneCannotSupplyRequestedCrypto() {
        assertEquals(BiometricSetupContinuation.CONFIRM_HARDWARE,
            resolveBiometricSetupContinuation(false, false, true, requiresCrypto = true))
    }
}
