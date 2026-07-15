/*
 *  Copyright (c) 2026 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.biometric.compat

import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModuleState
import java.util.concurrent.atomic.AtomicBoolean

internal fun BiometricAuthRequest.stateCacheKey(name: String): String {
    return "$name-$api-$type-$provider"
}

data class BiometricAuthState(
    val hardwareDetected: Boolean,
    val enrolled: Boolean,
    val lockedOut: Boolean,
    val permanentlyLocked: Boolean
) {
    val available: Boolean
        get() = hardwareDetected && enrolled

    val readyForUsage: Boolean
        get() = available && !lockedOut && !permanentlyLocked

    val readyForEnroll: Boolean
        get() = hardwareDetected && !lockedOut && !permanentlyLocked
}

enum class BiometricAuthRouteSource {
    LEGACY,
    BIOMETRIC_PROMPT
}

data class BiometricAuthRouteState(
    val request: BiometricAuthRequest,
    val source: BiometricAuthRouteSource,
    val state: BiometricAuthState
)

data class BiometricAuthSnapshot(
    val request: BiometricAuthRequest,
    val routes: List<BiometricAuthRouteState>,
    val state: BiometricAuthState
) {
    val available: Boolean
        get() = state.available

    val readyForUsage: Boolean
        get() = state.readyForUsage

    val readyForEnroll: Boolean
        get() = state.readyForEnroll
}

internal data class SelectedBiometricRoute(
    val type: BiometricType,
    val provider: BiometricProviderType,
    val usesBiometricPromptHardware: Boolean,
    val permissions: List<String>,
    val api: BiometricApi = BiometricApi.AUTO,
    val module: BiometricModule? = null
)

internal fun aggregateAnyBiometricState(states: Collection<BiometricAuthState>): BiometricAuthState {
    val detectedStates = states.filter { it.hardwareDetected }
    return BiometricAuthState(
        hardwareDetected = detectedStates.isNotEmpty(),
        enrolled = detectedStates.any { it.enrolled },
        lockedOut = detectedStates.any { it.lockedOut },
        permanentlyLocked = detectedStates.isNotEmpty() && detectedStates.all { it.permanentlyLocked }
    )
}

internal fun aggregateTypedAutoBiometricState(
    legacyState: BiometricAuthState,
    biometricPromptState: BiometricAuthState,
    preferLegacyEnrollment: Boolean
): BiometricAuthState {
    val detectedStates = listOf(legacyState, biometricPromptState).filter { it.hardwareDetected }
    if (detectedStates.isEmpty()) {
        return BiometricAuthState(
            hardwareDetected = false,
            enrolled = false,
            lockedOut = false,
            permanentlyLocked = false
        )
    }

    val enrolled = if (preferLegacyEnrollment && legacyState.hardwareDetected) {
        legacyState.enrolled
    } else {
        detectedStates.any { it.enrolled }
    }

    return BiometricAuthState(
        hardwareDetected = true,
        enrolled = enrolled,
        lockedOut = detectedStates.any { it.lockedOut },
        permanentlyLocked = detectedStates.all { it.permanentlyLocked }
    )
}

internal fun isSetupRouteSelectable(
    routeState: BiometricAuthState,
    moduleState: BiometricModuleState?,
    preferModule: Boolean
): Boolean {
    if (preferModule && moduleState != null) {
        return moduleState.hardwarePresent &&
                !moduleState.lockedOut &&
                !moduleState.permanentlyLocked
    }
    return routeState.hardwareDetected &&
            !routeState.lockedOut &&
            !routeState.permanentlyLocked
}

internal fun pickSelectedBiometricRoute(
    requestApi: BiometricApi,
    preferSystemFaceHardware: Boolean,
    preferHighPrioritySoftware: Boolean,
    biometricPromptRoute: SelectedBiometricRoute?,
    legacyHardwareRoute: SelectedBiometricRoute?,
    fallbackRoute: SelectedBiometricRoute?
): SelectedBiometricRoute? {
    return when (requestApi) {
        BiometricApi.BIOMETRIC_API -> biometricPromptRoute
        BiometricApi.LEGACY_API -> legacyHardwareRoute ?: fallbackRoute
        BiometricApi.AUTO -> when {
            preferSystemFaceHardware -> biometricPromptRoute ?: legacyHardwareRoute ?: fallbackRoute
            preferHighPrioritySoftware &&
                    fallbackRoute?.provider == BiometricProviderType.SOFTWARE -> fallbackRoute

            else -> biometricPromptRoute ?: legacyHardwareRoute ?: fallbackRoute
        }
    }
}

internal fun shouldKeepSystemEnrollType(route: SelectedBiometricRoute?): Boolean {
    return route?.type == BiometricType.BIOMETRIC_FACE &&
            route.provider == BiometricProviderType.HARDWARE
}

internal data class Api28StartAuthStagePlan(
    val shouldShowSystemPrompt: Boolean,
    val legacyAuthTypes: List<BiometricType>
)

internal fun planApi28StartAuthStage(
    confirmation: BiometricConfirmation = BiometricConfirmation.ALL,
    remainingPrimaryTypes: Collection<BiometricType>,
    remainingSecondaryTypes: Collection<BiometricType>,
    routeForType: (BiometricType) -> SelectedBiometricRoute?,
    requiresReadyExtrasBeforeAuthentication: (BiometricType) -> Boolean
): Api28StartAuthStagePlan {
    val shouldShowSystemPrompt = remainingPrimaryTypes.isNotEmpty()
    val legacyAuthTypes = if (!shouldShowSystemPrompt) {
        remainingSecondaryTypes.toList()
    } else if (confirmation == BiometricConfirmation.ANY) {
        emptyList()
    } else {
        remainingSecondaryTypes.filterNot { type ->
            val route = routeForType(type)
            route?.provider == BiometricProviderType.SOFTWARE &&
                    requiresReadyExtrasBeforeAuthentication(type)
        }
    }
    return Api28StartAuthStagePlan(
        shouldShowSystemPrompt = shouldShowSystemPrompt,
        legacyAuthTypes = legacyAuthTypes
    )
}

internal fun isSamsungDeviceModel(model: String?): Boolean {
    val normalized = model?.trim()?.lowercase() ?: return false
    return normalized.startsWith("samsung") ||
            normalized.startsWith("galaxy") ||
            normalized.startsWith("sm-")
}
/**
 * Starts a single authentication flow exactly once for a shared in-progress flag.
 */
internal fun AtomicBoolean.tryStartAuthFlow(): Boolean {
    return compareAndSet(false, true)
}

/**
 * Adds a caller-facing permission explanation only when the platform/OEM error did not provide one.
 */
internal fun AuthenticationResult.withMissingPermissionDescription(
    fallbackDescription: CharSequence
): AuthenticationResult {
    if (reason != AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR || !description.isNullOrBlank()) {
        return this
    }
    return copy(description = fallbackDescription)
}

internal fun normalizeBiometricErrorDescription(
    description: CharSequence?
): CharSequence? = description?.takeIf { it.isNotBlank() }

internal fun isSkippablePreparationError(errMsgId: Int): Boolean {
    return when (if (errMsgId < 1000) errMsgId else errMsgId % 1000) {
        dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_NO_PERMISSIONS,
        dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
        dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE -> true

        else -> false
    }
}

internal fun resolveEffectiveEnrollTypes(
    types: Collection<BiometricType>,
    hasSystemHardware: (BiometricType) -> Boolean,
    keepSystemType: (BiometricType) -> Boolean = { false },
    isActive: (BiometricType) -> Boolean
): List<BiometricType> {
    return types
        .filter { type -> keepSystemType(type) || !hasSystemHardware(type) }
        .filter(isActive)
}

internal enum class EnrollTerminalStatus {
    CONTINUE,
    SUCCEEDED,
    FAILED
}

internal data class EnrollTerminalOutcome(
    val status: EnrollTerminalStatus,
    val results: Set<AuthenticationResult>
)

internal data class EnrollSessionOutcome(
    val status: EnrollTerminalStatus,
    val results: Set<AuthenticationResult>,
    val confirmedThisRun: Boolean,
    val rollbackSuccessfulEnrolls: Boolean
)

internal fun resolvePreSatisfiedEnrollResults(
    scopeTypes: Collection<BiometricType>,
    pendingTypes: Collection<BiometricType>,
    isEnrolled: (BiometricType) -> Boolean
): Set<AuthenticationResult> {
    val pendingSet = pendingTypes.toHashSet()
    return scopeTypes
        .asSequence()
        .filterNot { type -> pendingSet.contains(type) }
        .filter(isEnrolled)
        .mapTo(LinkedHashSet()) { type ->
            AuthenticationResult(type)
        }
}

internal fun resolveEnrollTerminalOutcome(
    confirmation: BiometricConfirmation,
    scopeTypes: Collection<BiometricType>,
    successResults: Collection<AuthenticationResult>,
    failureResults: Collection<AuthenticationResult> = emptySet(),
    canceledResults: Collection<AuthenticationResult> = emptySet(),
    terminal: Boolean
): EnrollTerminalOutcome {
    val outcome = resolveEnrollSessionOutcome(
        confirmation = confirmation,
        scopeTypes = scopeTypes,
        successResults = successResults,
        confirmedTypes = successResults.mapNotNull { it.type }.toSet(),
        failureResults = failureResults,
        canceledResults = canceledResults,
        rollbackEligibleTypes = emptySet(),
        terminal = terminal
    )
    return EnrollTerminalOutcome(
        status = outcome.status,
        results = outcome.results
    )
}

internal fun resolveEnrollSessionOutcome(
    confirmation: BiometricConfirmation,
    scopeTypes: Collection<BiometricType>,
    successResults: Collection<AuthenticationResult>,
    confirmedTypes: Collection<BiometricType>,
    failureResults: Collection<AuthenticationResult> = emptySet(),
    canceledResults: Collection<AuthenticationResult> = emptySet(),
    rollbackEligibleTypes: Collection<BiometricType> = confirmedTypes,
    terminal: Boolean
): EnrollSessionOutcome {
    val scopeSet = scopeTypes.toCollection(LinkedHashSet())
    val successSet = successResults
        .filter { result -> result.type != null && scopeSet.contains(result.type) }
        .toCollection(LinkedHashSet())
    val successTypes = successSet.mapNotNull { it.type }.toHashSet()
    val confirmedSet = confirmedTypes
        .filter { type -> scopeSet.contains(type) }
        .toCollection(LinkedHashSet())
    val confirmedThisRun = confirmedSet.isNotEmpty()
    val isSatisfied = when (confirmation) {
        BiometricConfirmation.ANY -> successSet.isNotEmpty() && confirmedThisRun
        BiometricConfirmation.ALL -> {
            scopeSet.isNotEmpty() &&
                    successTypes.containsAll(scopeSet) &&
                    confirmedThisRun
        }
    }
    if (isSatisfied) {
        return EnrollSessionOutcome(
            status = EnrollTerminalStatus.SUCCEEDED,
            results = successSet,
            confirmedThisRun = true,
            rollbackSuccessfulEnrolls = false
        )
    }
    if (!terminal) {
        return EnrollSessionOutcome(
            status = EnrollTerminalStatus.CONTINUE,
            results = emptySet(),
            confirmedThisRun = confirmedThisRun,
            rollbackSuccessfulEnrolls = false
        )
    }
    val failureSet = when {
        failureResults.isNotEmpty() -> failureResults.toCollection(LinkedHashSet())
        canceledResults.isNotEmpty() -> canceledResults.toCollection(LinkedHashSet())
        else -> emptyEffectiveBiometricCancellationResults(scopeSet)
    }
    val rollbackSet = rollbackEligibleTypes
        .filter { type -> confirmedSet.contains(type) && successTypes.contains(type) }
        .toSet()
    return EnrollSessionOutcome(
        status = EnrollTerminalStatus.FAILED,
        results = failureSet,
        confirmedThisRun = confirmedThisRun,
        rollbackSuccessfulEnrolls = confirmation == BiometricConfirmation.ALL &&
                rollbackSet.isNotEmpty(),
    )
}

internal fun emptyEffectiveBiometricCancellationResults(
    allTypes: Collection<BiometricType>
): Set<AuthenticationResult> {
    val sourceTypes = if (allTypes.isEmpty()) {
        listOf(BiometricType.BIOMETRIC_ANY)
    } else {
        allTypes.toList()
    }
    return sourceTypes.mapTo(LinkedHashSet()) { type ->
        AuthenticationResult(
            type,
            reason = AuthenticationFailureReason.CANCELED
        )
    }
}
