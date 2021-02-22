package dev.skomlach.biometric.compat.utils.hardware

import dev.skomlach.biometric.compat.BiometricAuthRequest

abstract class AbstractHardware(val biometricAuthRequest: BiometricAuthRequest) : HardwareInfo {
    abstract override val isHardwareAvailable: Boolean
    abstract override val isBiometricEnrolled: Boolean
    abstract override val isLockedOut: Boolean
}