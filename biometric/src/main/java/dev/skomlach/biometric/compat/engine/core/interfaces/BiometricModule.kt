/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.core.Core

/**
 * A reprint module handles communication with a specific fingerprint api.
 *
 *
 * Implement this interface to add a new api to Core, then pass an instance of this interface to
 * [Core.registerModule]
 */

interface BiometricModule {
    val isManagerAccessible: Boolean
    val isHardwarePresent: Boolean
    val isLockOut: Boolean
    val isUserAuthCanByUsedWithCrypto: Boolean
    val hasEnrolled: Boolean

    /**
     * Start a fingerprint authentication request.
     *
     *
     * Don't call this method directly. Register an instance of this module with Core, then call
     * [Core.authenticate]
     *
     * @param cancellationSignal A signal that can cancel the authentication request.
     * @param listener           A listener that will be notified of the authentication status.
     * @param restartPredicate   If the predicate returns true, the module should ensure the sensor
     * is still running, and should not call any methods on the listener.
     * If the predicate returns false, the module should ensure the sensor
     * is not running before calling [AuthenticationListener.onFailure].
     */
    fun authenticate(
        biometricCryptoObject: BiometricCryptoObject?,
        cancellationSignal: CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    )

    /**
     * A tag uniquely identifying this class. It must be the same for all instances of each class,
     * and each class's tag must be unique among registered modules.
     */
    fun tag(): Int
}