package dev.skomlach.biometric.compat

data class BiometricAuthRequest(
    val api: BiometricApi = BiometricApi.AUTO,
    val type: BiometricType = BiometricType.BIOMETRIC_ANY,
    val confirmation: BiometricConfirmation = BiometricConfirmation.ANY
)