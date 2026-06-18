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


package dev.skomlach.biometric.compat

/**
 * Result payload delivered by biometric authentication callbacks.
 *
 * [reason] is non-null for failed or canceled flows. [cryptoSecurityLevel] mirrors
 * [cryptoObject] by default so callers can distinguish hardware-bound crypto from
 * app-managed fallback crypto without inspecting implementation details.
 */
data class AuthenticationResult(
    /** Biometric modality that produced the result, if it is known. */
    val type: BiometricType?,
    /** Optional crypto object returned by the successful authentication route. */
    val cryptoObject: BiometricCryptoObject? = null,
    /** Failure or cancellation category; null indicates no failure category was reported. */
    val reason: AuthenticationFailureReason? = null,
    /** Human-readable diagnostic or permission message suitable for logs/UI. */
    val description: CharSequence? = null,
    /** Security binding level for [cryptoObject] or an app-managed cryptography result. */
    val cryptoSecurityLevel: CryptoSecurityLevel =
        cryptoObject?.cryptoSecurityLevel ?: CryptoSecurityLevel.NONE
)
