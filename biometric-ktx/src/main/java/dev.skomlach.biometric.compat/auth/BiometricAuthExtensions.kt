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
package dev.skomlach.biometric.compat.auth

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.auth.helpers.AuthPromptHost
import dev.skomlach.biometric.compat.auth.helpers.BiometricAuthRequestData
import dev.skomlach.biometric.compat.auth.helpers.CoroutineAuthPromptCallback
import kotlinx.coroutines.suspendCancellableCoroutine

fun FragmentActivity.startBiometricAuthentication(
    biometricAuthRequestData: BiometricAuthRequestData = BiometricAuthRequestData(),
    callback: BiometricPromptCompat.AuthenticationCallback
): BiometricPromptCompat {
    return startBiometricAuthenticationInternal(
        AuthPromptHost(this),
        biometricAuthRequestData,
        callback
    )
}

suspend fun FragmentActivity.authenticateWithBiometrics(
    biometricAuthRequestData: BiometricAuthRequestData = BiometricAuthRequestData(),
): Set<AuthenticationResult> {
    val authPrompt = buildBiometricAuthPrompt(
        AuthPromptHost(this),
        biometricAuthRequestData
    )

    return authPrompt.authenticate()
}

fun Fragment.startBiometricAuthentication(
    biometricAuthRequestData: BiometricAuthRequestData = BiometricAuthRequestData(),
    callback: BiometricPromptCompat.AuthenticationCallback
): BiometricPromptCompat {
    return startBiometricAuthenticationInternal(
        AuthPromptHost(this),
        biometricAuthRequestData,
        callback
    )
}

suspend fun Fragment.authenticateWithBiometrics(
    biometricAuthRequestData: BiometricAuthRequestData = BiometricAuthRequestData(),
): Set<AuthenticationResult> {
    val authPrompt = buildBiometricAuthPrompt(
        AuthPromptHost(this),
        biometricAuthRequestData
    )

    return authPrompt.authenticate()
}

private suspend fun BiometricPromptCompat.authenticate(): Set<AuthenticationResult> {
    return suspendCancellableCoroutine { continuation ->
        this.authenticate(CoroutineAuthPromptCallback(continuation))
        continuation.invokeOnCancellation {
            this.cancelAuthentication()
        }
    }
}

private fun startBiometricAuthenticationInternal(
    host: AuthPromptHost,
    biometricAuthRequestData: BiometricAuthRequestData,
    callback: BiometricPromptCompat.AuthenticationCallback
): BiometricPromptCompat {
    val prompt = buildBiometricAuthPrompt(
        host,
        biometricAuthRequestData
    )

    prompt.authenticate(callback)
    return prompt
}

private fun buildBiometricAuthPrompt(
    host: AuthPromptHost,
    biometricAuthRequestData: BiometricAuthRequestData
): BiometricPromptCompat = BiometricPromptCompat.Builder(
    biometricAuthRequestData.biometricAuthRequest,
    (host.activity ?: host.fragment?.requireActivity())
        ?: throw IllegalArgumentException("BiometricPromptCompat require valid Activity or Fragment reference")
)
    .apply {
        biometricAuthRequestData.title?.let { setTitle(it) }
        biometricAuthRequestData.subtitle?.let { setSubtitle(it) }
        biometricAuthRequestData.description?.let { setDescription(it) }
        biometricAuthRequestData.negativeButton?.let { setNegativeButtonText(it) }
        biometricAuthRequestData.cryptographyPurpose?.let {
            setCryptographyPurpose(it)
        }
        setDeviceCredentialFallbackAllowed(biometricAuthRequestData.allowDeviceCredentialsFallback)
        if (biometricAuthRequestData.enableSilent) {
            enableSilentAuth(biometricAuthRequestData.authWindow)
        }
    }
    .build()
