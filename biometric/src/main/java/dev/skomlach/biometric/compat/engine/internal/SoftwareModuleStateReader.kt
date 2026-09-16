package dev.skomlach.biometric.compat.engine.internal

import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModuleState

internal fun readSoftwareModuleState(
    managerPresent: Boolean,
    moduleLockedOut: Boolean,
    hardwareDetected: () -> Boolean,
    hasEnrollment: () -> Boolean,
    lockoutError: () -> Int?,
    onError: (Exception) -> Unit,
    managerLockedOut: () -> Boolean = { false }
): BiometricModuleState {
    var hardware = false
    return try {
        hardware = managerPresent && hardwareDetected()
        val enrolled = hardware && hasEnrollment()
        val error = if (hardware) lockoutError() else null
        BiometricModuleState(
            managerAccessible = managerPresent && error != CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
            hardwarePresent = hardware,
            enrolled = enrolled,
            lockedOut = moduleLockedOut || error != null || (hardware && managerLockedOut()),
            permanentlyLocked = error == CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        )
    } catch (error: Exception) {
        onError(error)
        // An unreadable enrollment/lockout must not advertise an enrollment-ready module.
        BiometricModuleState(false, hardware, false, true, false)
    }
}
