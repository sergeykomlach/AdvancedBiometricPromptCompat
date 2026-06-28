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
