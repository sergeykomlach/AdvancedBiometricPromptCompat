package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.zkfinger.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ZkFingerLockoutOutcomeTest {
    @Test
    fun `temporary lockout maps to locked out failure`() {
        val outcome = zkFingerLockoutOutcomeForError(
            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT
        )

        assertEquals(AuthenticationFailureReason.LOCKED_OUT, outcome.reason)
        assertEquals(R.string.biometriccompat_zkfinger_help_too_many_attempts_try_later, outcome.messageResId)
    }

    @Test
    fun `permanent lockout maps to hardware unavailable failure`() {
        val outcome = zkFingerLockoutOutcomeForError(
            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        )

        assertEquals(AuthenticationFailureReason.HARDWARE_UNAVAILABLE, outcome.reason)
        assertEquals(R.string.biometriccompat_zkfinger_help_too_many_attempts_permanent, outcome.messageResId)
    }
}
