package dev.skomlach.biometric.compat.engine.internal.voice

import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.custom.voice.R
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceLockoutOutcomeTest {
    @Test
    fun temporaryLockoutMapsToLockedOutFailure() {
        val outcome = voiceLockoutOutcomeForError(
            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT
        )

        assertEquals(AuthenticationFailureReason.LOCKED_OUT, outcome.reason)
        assertEquals(R.string.biometriccompat_voice_help_lockout, outcome.messageResId)
    }

    @Test
    fun permanentLockoutMapsToHardwareUnavailableFailure() {
        val outcome = voiceLockoutOutcomeForError(
            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        )

        assertEquals(AuthenticationFailureReason.HARDWARE_UNAVAILABLE, outcome.reason)
        assertEquals(R.string.biometriccompat_voice_help_lockout_permanent, outcome.messageResId)
    }
}
