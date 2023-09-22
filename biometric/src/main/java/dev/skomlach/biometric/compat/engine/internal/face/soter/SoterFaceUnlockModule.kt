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

package dev.skomlach.biometric.compat.engine.internal.face.soter

import android.annotation.SuppressLint
import androidx.core.os.CancellationSignal
import com.tencent.soter.core.biometric.BiometricManagerCompat
import com.tencent.soter.core.model.ConstantsSoter
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper


class SoterFaceUnlockModule @SuppressLint("WrongConstant") constructor(private val listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACE_SOTERAPI) {
    private var manager: BiometricManagerCompat? = null

    init {
        manager = try {
            BiometricManagerCompat.from(context, ConstantsSoter.FACEID_AUTH)
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)
            null
        }
        listener?.initFinished(biometricMethod, this@SoterFaceUnlockModule)
    }

    override fun getManagers(): Set<Any> {
        //No way to detect enrollments
        return emptySet()
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
                return manager?.isHardwareDetected == true && manager?.hasEnrolledBiometric() == true
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
                val signalObject =
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
                val callback: BiometricManagerCompat.AuthenticationCallback =
                    AuthCallback(
                        biometricCryptoObject,
                        restartPredicate,
                        cancellationSignal,
                        listener
                    )

                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                val crypto = if (biometricCryptoObject == null) null else {
                    if (biometricCryptoObject.cipher != null)
                        BiometricManagerCompat.CryptoObject(biometricCryptoObject.cipher)
                    else if (biometricCryptoObject.mac != null)
                        BiometricManagerCompat.CryptoObject(biometricCryptoObject.mac)
                    else if (biometricCryptoObject.signature != null)
                        BiometricManagerCompat.CryptoObject(biometricCryptoObject.signature)
                    else
                        null
                }

                d("$name.authenticate:  Crypto=$crypto")
                authCallTimestamp.set(System.currentTimeMillis())
                it.authenticate(
                    crypto,
                    0,
                    signalObject,
                    callback,
                    ExecutorHelper.handler,
                    bundle ?: throw IllegalArgumentException("Bundle should be not NULL")
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
    ) : BiometricManagerCompat.AuthenticationCallback() {
        private var errorTs = System.currentTimeMillis()
        private val skipTimeout =
            context.resources.getInteger(android.R.integer.config_shortAnimTime)

        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence) {
            d("$name.onAuthenticationError: $errMsgId-$errString")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (errMsgId) {
                com.tencent.soter.core.biometric.FaceManager.FACE_ERROR_HW_UNAVAILABLE, com.tencent.soter.core.biometric.FaceManager.FACE_ERROR_CAMERA_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE

                com.tencent.soter.core.biometric.FaceManager.FACE_ERROR_UNABLE_TO_PROCESS -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED

                com.tencent.soter.core.biometric.FaceManager.FACE_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT

                com.tencent.soter.core.biometric.FaceManager.FACE_ERROR_LOCKOUT -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }

                com.tencent.soter.core.biometric.FaceManager.FACE_ERROR_CANCELED -> {
                    return
                }

                else -> {
                    //no-op
                }
            }
            if (restartCauseTimeout(failureReason)) {
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
                }
        }

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence) {
            d("$name.onAuthenticationHelp: $helpMsgId-$helpString")
            listener?.onHelp(helpString)
        }

        override fun onAuthenticationSucceeded(result: BiometricManagerCompat.AuthenticationResult) {
            d("$name.onAuthenticationSucceeded: $result; Crypto=${result.cryptoObject}")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return
            errorTs = tmp
            listener?.onSuccess(
                tag(),
                BiometricCryptoObject(
                    result.cryptoObject?.signature,
                    result.cryptoObject?.cipher,
                    result.cryptoObject?.mac
                )
            )
        }

        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.AUTHENTICATION_FAILED
            if (restartPredicate?.invoke(failureReason) == true) {
                listener?.onFailure(failureReason, tag())
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
            }
        }
    }

}