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

package dev.skomlach.biometric.compat.engine.core

import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import java.util.*
import kotlin.collections.set


object Core {
    private val cancellationSignals =
        Collections.synchronizedMap(HashMap<BiometricModule, CancellationSignal>())
    private val reprintModuleHashMap = Collections.synchronizedMap(HashMap<Int, BiometricModule>())

    fun cleanModules() {
        reprintModuleHashMap.clear()
    }

    fun registerModule(module: BiometricModule?) {
        if (module == null || reprintModuleHashMap.containsKey(module.tag())) {
            return
        }
        if (module.isHardwarePresent) {
            reprintModuleHashMap[module.tag()] = module
        }
    }

    val isLockOut: Boolean
        get() {
            for (module in reprintModuleHashMap.values) {
                if (module.isLockOut) {
                    return true
                }
            }
            return false
        }

    val isHardwareDetected: Boolean
        get() {
            for (module in reprintModuleHashMap.values) {
                if (module.isHardwarePresent) return true
            }
            return false
        }

    fun hasEnrolled(): Boolean {
        for (module in reprintModuleHashMap.values) {
            if (module.hasEnrolled()) return true
        }
        return false
    }
    /**
     * Start an authentication request.
     *
     * @param listener         The listener to be notified.
     * @param restartPredicate The predicate that determines whether to restart or not.
     */
    /**
     * Start a fingerprint authentication request.
     *
     *
     * Equivalent to calling [.authenticate] with
     * [RestartPredicatesImpl.defaultPredicate]
     *
     * @param listener The listener that will be notified of authentication events.
     */

    @JvmOverloads
    fun authenticate(
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate? = RestartPredicatesImpl.defaultPredicate()
    ) {
        for (module in reprintModuleHashMap.values) {
            authenticate(module, listener, restartPredicate)
        }
    }

    fun authenticate(
        module: BiometricModule,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        if (!module.isHardwarePresent || !module.hasEnrolled() || module.isLockOut) throw RuntimeException(
            "Module " + module.javaClass.simpleName + " not ready"
        )
        var cancellationSignal = cancellationSignals[module]
        if (cancellationSignal != null && !cancellationSignal.isCanceled) cancelAuthentication(
            module
        )
        cancellationSignal = CancellationSignal()
        cancellationSignals[module] = cancellationSignal
        module.authenticate(cancellationSignal, listener, restartPredicate)
    }

    fun cancelAuthentication() {
        for (module in reprintModuleHashMap.values) {
            cancelAuthentication(module)
        }
    }


    fun cancelAuthentication(module: BiometricModule) {
        val signal = cancellationSignals[module]
        if (signal != null && !signal.isCanceled) {
            signal.cancel()
        }
        cancellationSignals.remove(module)
    }

    /**
     * Start a fingerprint authentication request.
     *
     *
     * This variant will not restart the fingerprint reader after any failure, including non-fatal
     * failures.
     *
     * @param listener The listener that will be notified of authentication events.
     */

    fun authenticateWithoutRestart(listener: AuthenticationListener?) {
        authenticate(listener, RestartPredicatesImpl.neverRestart())
    }
}