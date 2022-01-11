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
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.DummyBiometricModule
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.util.*
import kotlin.collections.set


object Core {
    private val cancellationSignals =
        Collections.synchronizedMap(HashMap<BiometricModule, CancellationSignal>())
    private val reprintModuleHashMap = Collections.synchronizedMap(HashMap<Int, BiometricModule>())

    @Synchronized
    fun cleanModules() {
        try {
            reprintModuleHashMap.clear()
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    @Synchronized
    fun registerModule(module: BiometricModule?) {
        try {
            if (module == null || reprintModuleHashMap.containsKey(module.tag())) {
                return
            }
            if (module.isHardwarePresent) {
                reprintModuleHashMap[module.tag()] = module
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    val isLockOut: Boolean
        @Synchronized
        get() {
            try {
                for (module in reprintModuleHashMap.values) {
                    if (module.isLockOut) {
                        return true
                    }
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
            return false
        }
    val isHardwareDetected: Boolean
        @Synchronized
        get() {
            try {
                for (module in reprintModuleHashMap.values) {
                    if (module.isHardwarePresent) return true
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
            return false
        }

    @Synchronized
    fun hasEnrolled(): Boolean {
        try {
            for (module in reprintModuleHashMap.values) {
                if (module.hasEnrolled()) return true
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
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
    @Synchronized
    @JvmOverloads
    fun authenticate(
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate? = RestartPredicatesImpl.defaultPredicate()
    ) {
        var m: BiometricModule? = null
        try {
            for (module in reprintModuleHashMap.values) {
                m = module
                authenticate(module, listener, restartPredicate)
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
            listener?.onFailure(
                AuthenticationFailureReason.INTERNAL_ERROR,
                m?.tag() ?: DummyBiometricModule(null).tag()
            )
        }
    }

    @Synchronized
    fun authenticate(
        module: BiometricModule,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        try {
            if (!module.isHardwarePresent || !module.hasEnrolled() || module.isLockOut) throw RuntimeException(
                "Module " + module.javaClass.simpleName + " not ready"
            )
            cancelAuthentication(module)
            val cancellationSignal = CancellationSignal()
            cancellationSignals[module] = cancellationSignal
            module.authenticate(cancellationSignal, listener, restartPredicate)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
            listener?.onFailure(AuthenticationFailureReason.INTERNAL_ERROR, module.tag())
        }
    }

    @Synchronized
    fun cancelAuthentication() {
        for (module in reprintModuleHashMap.values) {
            cancelAuthentication(module)
        }
    }

    @Synchronized
    fun cancelAuthentication(module: BiometricModule) {
        try {
            val signal = cancellationSignals[module]
            if (signal != null && !signal.isCanceled) {
                signal.cancel()
            }
            cancellationSignals.remove(module)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
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
    @Synchronized
    fun authenticateWithoutRestart(listener: AuthenticationListener?) {
        authenticate(listener, RestartPredicatesImpl.neverRestart())
    }
}