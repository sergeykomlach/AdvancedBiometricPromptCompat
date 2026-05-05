/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.compat.impl

import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricPromptCompat

interface IBiometricPromptImpl {
    fun authenticate(callback: BiometricPromptCompat.AuthenticationCallback?)
    fun cancelAuthentication()
    val builder: BiometricPromptCompat.Builder
}

internal fun Set<AuthenticationResult>.containsInternalError(): Boolean =
    any { it.reason == AuthenticationFailureReason.INTERNAL_ERROR }

internal fun BiometricPromptCompat.AuthenticationCallback?.dispatchCanceledOrFailed(
    results: Set<AuthenticationResult>
) {
    if (results.containsInternalError()) {
        this?.onFailed(results)
    } else {
        this?.onCanceled(results)
    }
}

internal fun Throwable.isNoKeystoreBiometricEnrollment(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        val msg = current.message.orEmpty()
        if (msg.contains("At least one biometric", ignoreCase = true) &&
            msg.contains("must be enrolled to create keys", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
    }
    return false
}
