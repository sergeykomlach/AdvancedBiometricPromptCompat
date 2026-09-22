package dev.skomlach.biometric.compat.impl

import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricType

internal enum class AuthenticationCompletion { PENDING, SUCCEEDED, FAILED }

internal fun requiresSensorSpecificRoute(type: BiometricType, usesSystemPrompt: Boolean, deviceCredential: Boolean): Boolean =
    type != BiometricType.BIOMETRIC_ANY && usesSystemPrompt && !deviceCredential

internal fun resolveApi28Completion(
    confirmation: BiometricConfirmation,
    availableTypes: Collection<BiometricType>,
    softwareEnrollmentTargets: Collection<BiometricType>,
    hardwareConfirmation: AuthResult.AuthResultState?,
    results: Map<out BiometricType?, AuthResult>,
    softwarePreparationPending: Boolean = false
): AuthenticationCompletion {
    if (softwareEnrollmentTargets.isNotEmpty()) {
        when (hardwareConfirmation) {
            AuthResult.AuthResultState.FATAL_ERROR -> return AuthenticationCompletion.FAILED
            AuthResult.AuthResultState.SUCCESS -> Unit
            else -> return AuthenticationCompletion.PENDING
        }
    }
    val completion = resolveAuthenticationCompletion(
        confirmation,
        softwareEnrollmentTargets.ifEmpty { availableTypes },
        results
    )
    return if (softwarePreparationPending && softwareEnrollmentTargets.isEmpty() &&
        confirmation == BiometricConfirmation.ANY && completion == AuthenticationCompletion.FAILED
    ) AuthenticationCompletion.PENDING else completion
}

// A system prompt reports no individual modality. One success cannot prove both face and finger.
internal fun canConfirmSystemModalities(
    confirmation: BiometricConfirmation,
    primaryTypes: Collection<BiometricType>
): Boolean = confirmation != BiometricConfirmation.ALL || primaryTypes.distinct().size <= 1

internal fun resolveAuthenticationCompletion(
    confirmation: BiometricConfirmation,
    requiredTypes: Collection<BiometricType>,
    results: Map<out BiometricType?, AuthResult>
): AuthenticationCompletion {
    if (requiredTypes.isEmpty()) return AuthenticationCompletion.FAILED
    val selected = requiredTypes.map { results[it]?.authResultState }
    return when (confirmation) {
        BiometricConfirmation.ALL -> when {
            selected.any { it == AuthResult.AuthResultState.FATAL_ERROR } -> AuthenticationCompletion.FAILED
            selected.all { it == AuthResult.AuthResultState.SUCCESS } -> AuthenticationCompletion.SUCCEEDED
            else -> AuthenticationCompletion.PENDING
        }
        BiometricConfirmation.ANY -> when {
            selected.any { it == AuthResult.AuthResultState.SUCCESS } -> AuthenticationCompletion.SUCCEEDED
            selected.all { it == AuthResult.AuthResultState.FATAL_ERROR } -> AuthenticationCompletion.FAILED
            else -> AuthenticationCompletion.PENDING
        }
    }
}
