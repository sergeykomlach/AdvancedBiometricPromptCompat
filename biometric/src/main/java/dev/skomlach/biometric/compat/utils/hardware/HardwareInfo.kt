package dev.skomlach.biometric.compat.utils.hardware

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface HardwareInfo {
    val isHardwareAvailable: Boolean
    val isBiometricEnrolled: Boolean
    val isLockedOut: Boolean
}