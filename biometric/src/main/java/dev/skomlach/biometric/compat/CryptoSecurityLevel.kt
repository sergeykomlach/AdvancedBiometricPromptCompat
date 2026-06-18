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

/**
 * Security binding level of the crypto object returned with an authentication result.
 */
enum class CryptoSecurityLevel {
    /** No cryptographic binding is present. */
    NONE,
    /** Crypto is bound to Android Keystore or another hardware-backed biometric flow. */
    HARDWARE_BACKED,
    /** App-managed crypto flow succeeded but is not directly bound to biometric hardware. */
    APP_FLOW_NOT_BIOMETRIC_BOUND
}
