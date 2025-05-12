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

package dev.skomlach.biometric.compat.engine.core.interfaces

import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject

/**
 * A listener that is notified of the results of fingerprint authentication.
 */

interface AuthenticationListener {
    fun onHelp(msg: CharSequence?)

    /**
     * Called after a fingerprint is successfully authenticated.
     *
     * @param moduleTag The [BiometricModule.tag] of the module that was used for authentication.
     */
    fun onSuccess(moduleTag: Int, biometricCryptoObject: BiometricCryptoObject?)

    /**
     * Called after an error or authentication failure.
     *
     * @param failureReason The general reason for the failure.
     * @param moduleTag     The [BiometricModule.tag] of the module that is currently active. This is
     * useful to know the meaning of the error code.
     */
    fun onFailure(
        moduleTag: Int,
        reason: AuthenticationFailureReason?,
        description: CharSequence?
    )

    fun onCanceled(
        moduleTag: Int,
        reason: AuthenticationFailureReason?,
        description: CharSequence?
    )
}