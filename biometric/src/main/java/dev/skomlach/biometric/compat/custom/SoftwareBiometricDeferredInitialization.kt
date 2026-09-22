package dev.skomlach.biometric.compat.custom

/**
 * Optional, nonblocking initial-readiness contract. NEW/PREPARING are not failure.
 * READY and FAILED are terminal initial outcomes: a later runtime fault or lockout must not
 * change READY into FAILED and thereby authorize switching to another enrollment namespace.
 * Opting in also permits prepareForAuthentication before enrollment/permission routing: initial
 * preparation must only load/check the engine, never capture input, request permissions or show UI.
 */
interface SoftwareBiometricDeferredInitialization {
    val initializationState: SoftwareBiometricInitializationState
}

enum class SoftwareBiometricInitializationState {
    NEW, PREPARING, READY, FAILED
}
