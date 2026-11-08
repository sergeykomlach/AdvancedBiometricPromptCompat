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
import dev.skomlach.biometric.compat.crypto.AppFlowCryptoFacade
import dev.skomlach.biometric.compat.crypto.BiometricCryptoObjectHelper
import dev.skomlach.biometric.compat.CryptoSecurityLevel
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.StatusAuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.onStatus
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.ExecutorHelper
import java.util.Collections
import java.util.WeakHashMap


object Core {

    private val cancellationSignals =
        Collections.synchronizedMap(HashMap<BiometricModule, CancellationSignal>())
    // Values deliberately contain no module/signal references, including after local teardown.
    private val requestOwners = WeakHashMap<BiometricModule, ModuleRequestOwner>()
    private val reprintModuleHashMap = Collections.synchronizedMap(HashMap<Int, BiometricModule>())
    fun cleanModules() {
        try {
            synchronized(reprintModuleHashMap) { reprintModuleHashMap.clear() }
            val requests = synchronized(cancellationSignals) {
                requestOwners.values.forEach { it.supersede() }
                requestOwners.clear()
                cancellationSignals.toMap()
            }
            requests.forEach { (module, signal) -> cancelAuthentication(module, signal) }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }


    fun registerModule(module: BiometricModule?) {
        try {
            if (module == null) return
            val tag = module.tag()
            if (reprintModuleHashMap.containsKey(tag)) {
                return
            }
            if (module.isHardwarePresent) {
                registerSelectedModule(module)
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    internal fun registerSelectedModule(module: BiometricModule) {
        val tag = module.tag()
        synchronized(reprintModuleHashMap) {
            if (!reprintModuleHashMap.containsKey(tag)) reprintModuleHashMap[tag] = module
        }
    }

    val isLockOut: Boolean
        get() {
            try {

                val modules = synchronized(reprintModuleHashMap) { reprintModuleHashMap.values.toList() }
                for (module in modules) {
                    if (module.getModuleState().lockedOut) {
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

                val modules = synchronized(reprintModuleHashMap) { reprintModuleHashMap.values.toList() }
                for (module in modules) {
                    if (module.getModuleState().hardwarePresent) return true
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
            return false
        }


    val hasEnrolled: Boolean
        get() {
            try {

                val modules = synchronized(reprintModuleHashMap) { reprintModuleHashMap.values.toList() }
                for (module in modules) {
                    if (module.getModuleState().enrolled) return true
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
        restartPredicate: RestartPredicate? = RestartPredicatesImpl.defaultPredicate(),
        allowCryptoFallback: Boolean = false
    ) = authenticateSelected(purpose, listener, restartPredicate, allowCryptoFallback)

    internal fun authenticateSelected(
        purpose: BiometricCryptographyPurpose?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate? = RestartPredicatesImpl.defaultPredicate(),
        allowCryptoFallback: Boolean = false,
        moduleTags: Set<Int>? = null,
        isActive: () -> Boolean = { true }
    ) {
        val ownedPurpose = purpose?.copy(initVector = purpose.initVector?.copyOf())
        val modules = synchronized(reprintModuleHashMap) { reprintModuleHashMap.values.toList() }
        for (module in modules) {
            if (!isActive()) return
            if (moduleTags != null && module.tag() !in moduleTags) continue
            if (ownedPurpose == null) {
                startRequest(null, module, listener, restartPredicate, isActive = isActive)
            } else {
                val keyName = "BiometricModule${module.tag()}"
                startRequest(null, module, listener, restartPredicate, isActive, keyName) {
                    val appFlow = !module.isUserAuthCanByUsedWithCrypto
                    if (appFlow) {
                        check(allowCryptoFallback) { "This operation requires biometric-bound cryptography" }
                        BiometricCryptoObjectHelper.prepareAppFlowCryptoObject(keyName, ownedPurpose)
                    } else {
                        BiometricCryptoObjectHelper.prepareBiometricCryptoObject(keyName, ownedPurpose)
                    }
                }
            }
        }
    }

    fun authenticate(
        biometricCryptoObject: BiometricCryptoObject?,
        module: BiometricModule,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        startRequest(biometricCryptoObject, module, listener, restartPredicate)
    }

    private fun startRequest(
        crypto: BiometricCryptoObject?,
        module: BiometricModule,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?,
        isActive: () -> Boolean = { true },
        cryptoKeyName: String? = null,
        prepare: (() -> BiometricCryptoObject)? = null
    ) {
        val signal = CancellationSignal()
        val owner = ModuleRequestOwner()
        fun ownsRequest() = owner.isCurrent() && !signal.isCanceled && isActive()
        val previous = synchronized(cancellationSignals) {
            requestOwners.put(module, owner)?.supersede()
            cancellationSignals.put(module, signal)
        }
        previous?.let(::cancelSignal)
        if (!ownsRequest()) return

        fun launch(prepared: BiometricCryptoObject?) {
            if (!ownsRequest()) return
            val appFlow = prepare != null &&
                prepared?.cryptoSecurityLevel == CryptoSecurityLevel.APP_FLOW_NOT_BIOMETRIC_BOUND
            val requestListener = ModuleFeedbackListener(
                if (appFlow) listener.withPreparedCryptoObject(prepared) else listener,
                module.tag(), owner, cancelRequest = { cancelAuthentication(module, signal) },
                isActive = { !signal.isCanceled && isActive() }
            )
            try {
                if (cryptoKeyName != null) {
                    if (appFlow) AppFlowCryptoFacade.registerKeyForAppFlow(cryptoKeyName)
                    else AppFlowCryptoFacade.registerKeyForBiometric(cryptoKeyName)
                }
                module.authenticate(if (appFlow) null else prepared, signal, requestListener, restartPredicate)
            } catch (error: Exception) {
                BiometricLoggerImpl.e(error)
                cancelAuthentication(module, signal)
                requestListener.onFailure(module.tag(), AuthenticationFailureReason.INTERNAL_ERROR, error.message)
            }
        }

        if (prepare == null) {
            launch(crypto)
        } else {
            ExecutorHelper.startOnBackground {
                if (!ownsRequest()) return@startOnBackground
                val result = runCatching(prepare)
                ExecutorHelper.post {
                    if (!ownsRequest()) return@post
                    result.onSuccess(::launch).onFailure { error ->
                        BiometricLoggerImpl.e(error)
                        cancelAuthentication(module, signal)
                        listener?.onFailure(module.tag(), AuthenticationFailureReason.CRYPTO_ERROR, error.message)
                    }
                }
            }
        }
    }

    fun cancelAuthentication() {
        val requests = synchronized(cancellationSignals) { cancellationSignals.toMap() }
        // Keep later origins discoverable until their own teardown. A reentrant start
        // must finish its predecessor's SDK cancellation before starting new capture.
        requests.forEach { (module, signal) -> cancelAuthentication(module, signal) }
    }


    fun cancelAuthentication(module: BiometricModule) {
        val signal = synchronized(cancellationSignals) { cancellationSignals.remove(module) }
        signal?.let(::cancelSignal)
    }

    private fun cancelAuthentication(module: BiometricModule, expected: CancellationSignal) {
        val signal = synchronized(cancellationSignals) {
            if (cancellationSignals[module] !== expected) null else cancellationSignals.remove(module)
        }
        signal?.let(::cancelSignal)
    }

    private fun cancelSignal(signal: CancellationSignal) {
        try {
            if (!signal.isCanceled) signal.cancel()
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

    private fun AuthenticationListener?.withPreparedCryptoObject(
        biometricCryptoObject: BiometricCryptoObject?
    ): AuthenticationListener? {
        val delegate = this ?: return null
        return object : StatusAuthenticationListener {
            override fun onHelp(msg: CharSequence?) {
                delegate.onHelp(msg)
            }

            override fun onStatus(moduleTag: Int, status: dev.skomlach.biometric.compat.custom.SoftwarePromptStatus) {
                delegate.onStatus(moduleTag, status)
            }

            override fun onSuccess(
                moduleTag: Int,
                resultCryptoObject: BiometricCryptoObject?
            ) {
                // This module was called without crypto; only the prepared app-flow object is authoritative.
                delegate.onSuccess(moduleTag, biometricCryptoObject)
            }

            override fun onFailure(
                moduleTag: Int,
                reason: AuthenticationFailureReason?,
                description: CharSequence?
            ) {
                delegate.onFailure(moduleTag, reason, description)
            }

            override fun onCanceled(
                moduleTag: Int,
                reason: AuthenticationFailureReason?,
                description: CharSequence?
            ) {
                delegate.onCanceled(moduleTag, reason, description)
            }
        }
    }
}
