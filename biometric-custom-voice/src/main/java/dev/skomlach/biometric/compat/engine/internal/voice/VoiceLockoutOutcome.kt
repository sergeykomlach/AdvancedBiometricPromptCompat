package dev.skomlach.biometric.compat.engine.internal.voice

import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.custom.voice.R

internal data class VoiceLockoutOutcome(
    val reason: AuthenticationFailureReason,
    val messageResId: Int
)

internal fun voiceLockoutOutcomeForError(error: Int): VoiceLockoutOutcome {
    return when (error) {
        AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> {
            VoiceLockoutOutcome(
                reason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE,
                messageResId = R.string.biometriccompat_voice_help_lockout_permanent
            )
        }

        else -> {
            VoiceLockoutOutcome(
                reason = AuthenticationFailureReason.LOCKED_OUT,
                messageResId = R.string.biometriccompat_voice_help_lockout
            )
        }
    }
}
