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

package dev.skomlach.biometric.compat.engine.internal.fingerprint

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper

@TargetApi(Build.VERSION_CODES.M)

class API23FingerprintModule @SuppressLint("WrongConstant") constructor(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FINGERPRINT_API23) {
    private var manager: FingerprintManager? = null

    init {

        try {
            manager = ContextCompat.getSystemService(
                context,
                FingerprintManager::class.java
            )
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)

        }


        if (manager == null) {
            try {
                manager = context.getSystemService("fingerprint") as FingerprintManager?
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)

            }
        }
        listener?.initFinished(biometricMethod, this@API23FingerprintModule)
    }

    override fun getManagers(): Set<Any> {
        val managers = HashSet<Any>()
        manager?.let {
            managers.add(it)
        }
        return managers
    }

    override val isManagerAccessible: Boolean
        get() = manager != null
    override val isHardwarePresent: Boolean
        get() {

            try {
                return manager?.isHardwareDetected == true
            } catch (e: Throwable) {

            }

            return false
        }

    override val hasEnrolled: Boolean
        get() {

            try {
                return manager?.hasEnrolledFingerprints() == true
            } catch (e: Throwable) {

            }

            return false
        }

    @Throws(SecurityException::class)
    override fun authenticate(
        biometricCryptoObject: BiometricCryptoObject?,
        cancellationSignal: CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        manager?.let {
            try {
                // Why getCancellationSignalObject returns an Object is unexplained
                (if (cancellationSignal == null) null else cancellationSignal.cancellationSignalObject as android.os.CancellationSignal?)
                    ?: throw IllegalArgumentException("CancellationSignal cann't be null")

                this.originalCancellationSignal = cancellationSignal
                authenticateInternal(biometricCryptoObject, listener, restartPredicate)
                return
            } catch (e: Throwable) {
                e(e, "$name: authenticate failed unexpectedly")
            }
        }
        listener?.onFailure(AuthenticationFailureReason.UNKNOWN, tag())
        return
    }

    private fun authenticateInternal(
        biometricCryptoObject: BiometricCryptoObject?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        d("$name.authenticate - $biometricMethod; Crypto=$biometricCryptoObject")
        manager?.let {
            try {
                val cancellationSignal = CancellationSignal()
                originalCancellationSignal?.setOnCancelListener {
                    if (!cancellationSignal.isCanceled)
                        cancellationSignal.cancel()
                }
                // Why getCancellationSignalObject returns an Object is unexplained
                val signalObject =
                    (cancellationSignal.cancellationSignalObject as android.os.CancellationSignal?)
                        ?: throw IllegalArgumentException("CancellationSignal cann't be null")
                val callback: FingerprintManager.AuthenticationCallback =
                    AuthCallback(
                        biometricCryptoObject,
                        restartPredicate,
                        cancellationSignal,
                        listener
                    )

                val crypto = if (biometricCryptoObject == null) null else {
                    if (biometricCryptoObject.cipher != null)
                        FingerprintManager.CryptoObject(biometricCryptoObject.cipher)
                    else if (biometricCryptoObject.mac != null)
                        FingerprintManager.CryptoObject(biometricCryptoObject.mac)
                    else if (biometricCryptoObject.signature != null)
                        FingerprintManager.CryptoObject(biometricCryptoObject.signature)
                    else
                        null
                }

                d("$name.authenticate:  Crypto=$crypto")
                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                authCallTimestamp.set(System.currentTimeMillis())
                it.authenticate(
                    crypto,
                    signalObject,
                    0,
                    callback,
                    ExecutorHelper.handler
                )
                return
            } catch (e: Throwable) {
                e(e, "$name: authenticate failed unexpectedly")
            }
        }
        listener?.onFailure(AuthenticationFailureReason.UNKNOWN, tag())
        return
    }

    internal inner class AuthCallback(
        private val biometricCryptoObject: BiometricCryptoObject?,
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: CancellationSignal?,
        private val listener: AuthenticationListener?
    ) : FingerprintManager.AuthenticationCallback() {
        private var errorTs = 0L
        private val skipTimeout =
            context.resources.getInteger(android.R.integer.config_shortAnimTime)
        private var selfCanceled = false

        @Deprecated("Deprecated in Java")
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
            d("$name.onAuthenticationError: $errMsgId-$errString")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout)
                return
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (if (errMsgId < 1000) errMsgId else errMsgId % 1000) {
                FingerprintManager.FINGERPRINT_ERROR_NO_FINGERPRINTS -> failureReason =
                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED

                FingerprintManager.FINGERPRINT_ERROR_HW_NOT_PRESENT -> failureReason =
                    AuthenticationFailureReason.NO_HARDWARE

                FingerprintManager.FINGERPRINT_ERROR_HW_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE

                FingerprintManager.FINGERPRINT_ERROR_LOCKOUT_PERMANENT -> {
                    BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(
                        biometricMethod.biometricType
                    )
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                }

                FingerprintManager.FINGERPRINT_ERROR_UNABLE_TO_PROCESS -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED

                FingerprintManager.FINGERPRINT_ERROR_NO_SPACE -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED

                FingerprintManager.FINGERPRINT_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT

                FingerprintManager.FINGERPRINT_ERROR_LOCKOUT -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }

                FingerprintManager.FINGERPRINT_ERROR_CANCELED, FingerprintManager.FINGERPRINT_ERROR_USER_CANCELED -> {
                    if (!selfCanceled) {
                        listener?.onCanceled(tag())
                        Core.cancelAuthentication(this@API23FingerprintModule)
                    }
                    return
                }

                else -> {
                    if (!selfCanceled) {
                        listener?.onFailure(failureReason, tag())
                        postCancelTask {
                            if (cancellationSignal?.isCanceled == false) {
                                selfCanceled = true
                                listener?.onCanceled(tag())
                                Core.cancelAuthentication(this@API23FingerprintModule)
                            }
                        }
                    }
                    return
                }
            }
            if (restartCauseTimeout(failureReason)) {
                selfCanceled = true
                cancellationSignal?.cancel()
                ExecutorHelper.postDelayed({
                    authenticateInternal(biometricCryptoObject, listener, restartPredicate)
                }, skipTimeout.toLong())
            } else
                if (failureReason == AuthenticationFailureReason.TIMEOUT || restartPredicate?.invoke(
                        failureReason
                    ) == true
                ) {
                    listener?.onFailure(failureReason, tag())
                    selfCanceled = true
                    cancellationSignal?.cancel()
                    ExecutorHelper.postDelayed({
                        authenticateInternal(biometricCryptoObject, listener, restartPredicate)
                    }, skipTimeout.toLong())
                } else {
                    if (mutableListOf(
                            AuthenticationFailureReason.SENSOR_FAILED,
                            AuthenticationFailureReason.AUTHENTICATION_FAILED
                        ).contains(failureReason)
                    ) {
                        lockout()
                        failureReason = AuthenticationFailureReason.LOCKED_OUT
                    }
                    listener?.onFailure(failureReason, tag())
                    postCancelTask {
                        if (cancellationSignal?.isCanceled == false) {
                            selfCanceled = true
                            listener?.onCanceled(tag())
                            Core.cancelAuthentication(this@API23FingerprintModule)
                        }
                    }
                }
        }

        @Deprecated("Deprecated in Java")
        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
            d("$name.onAuthenticationHelp: $helpMsgId-$helpString")
            listener?.onHelp(helpString)
        }

        @Deprecated("Deprecated in Java")
        override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
            d("$name.onAuthenticationSucceeded: $result; Crypto=${result?.cryptoObject}")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return
            errorTs = tmp
            listener?.onSuccess(
                tag(),
                BiometricCryptoObject(
                    result?.cryptoObject?.signature,
                    result?.cryptoObject?.cipher,
                    result?.cryptoObject?.mac
                )
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            listener?.onFailure(AuthenticationFailureReason.AUTHENTICATION_FAILED, tag())
        }
    }

}