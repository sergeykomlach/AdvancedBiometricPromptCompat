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

package dev.skomlach.biometric.compat.engine.core

import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.BiometricCryptographyPurpose
import dev.skomlach.biometric.compat.crypto.BiometricCryptoException
import dev.skomlach.biometric.compat.crypto.BiometricCryptoObjectHelper
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
    fun cleanModules() {
        try {
            reprintModuleHashMap.clear()
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }


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


    val hasEnrolled: Boolean
        get() {
            try {

                for (module in reprintModuleHashMap.values) {
                    if (module.hasEnrolled) return true
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

    @JvmOverloads
    fun authenticate(
        purpose: BiometricCryptographyPurpose?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate? = RestartPredicatesImpl.defaultPredicate()
    ) {
        var m: BiometricModule? = null
        try {

            for (module in reprintModuleHashMap.values) {
                m = module

                var biometricCryptoObject: BiometricCryptoObject? = null
                purpose?.let {
                    try {
                        biometricCryptoObject =
                            BiometricCryptoObjectHelper.getBiometricCryptoObject(
                                "BiometricModule${module.tag()}",
                                purpose,
                                m.isUserAuthCanByUsedWithCrypto
                            )
                    } catch (e: BiometricCryptoException) {
                        if (purpose.purpose == BiometricCryptographyPurpose.ENCRYPT) {
                            BiometricCryptoObjectHelper.deleteCrypto("BiometricModule${module.tag()}")
                            biometricCryptoObject =
                                BiometricCryptoObjectHelper.getBiometricCryptoObject(
                                    "BiometricModule${module.tag()}",
                                    purpose,
                                    m.isUserAuthCanByUsedWithCrypto
                                )
                        } else throw e
                    }
                }


                authenticate(biometricCryptoObject, module, listener, restartPredicate)
            }
        } catch (e: BiometricCryptoException) {
            BiometricLoggerImpl.e(e)
            listener?.onFailure(
                AuthenticationFailureReason.CRYPTO_ERROR,
                m?.tag() ?: DummyBiometricModule(null).tag()
            )
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
            listener?.onFailure(
                AuthenticationFailureReason.INTERNAL_ERROR,
                m?.tag() ?: DummyBiometricModule(null).tag()
            )
        }
    }


    fun authenticate(
        biometricCryptoObject: BiometricCryptoObject?,
        module: BiometricModule,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        try {

            if (!module.isHardwarePresent || !module.hasEnrolled || module.isLockOut) throw RuntimeException(
                "Module " + module.javaClass.simpleName + " not ready"
            )
            cancelAuthentication(module)
            val cancellationSignal = CancellationSignal()
            cancellationSignals[module] = cancellationSignal
            module.authenticate(
                biometricCryptoObject,
                cancellationSignal,
                listener,
                restartPredicate
            )
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
            listener?.onFailure(AuthenticationFailureReason.INTERNAL_ERROR, module.tag())
        }
    }


    fun cancelAuthentication() {
        for (module in reprintModuleHashMap.values) {
            cancelAuthentication(module)
        }
    }


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

    fun authenticateWithoutRestart(
        biometricCryptoObject: BiometricCryptographyPurpose?,
        listener: AuthenticationListener?
    ) {
        authenticate(biometricCryptoObject, listener, RestartPredicatesImpl.neverRestart())
    }
}