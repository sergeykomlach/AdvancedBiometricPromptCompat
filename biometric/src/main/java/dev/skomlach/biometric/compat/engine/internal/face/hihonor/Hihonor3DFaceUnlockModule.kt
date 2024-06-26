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

package dev.skomlach.biometric.compat.engine.internal.face.hihonor

import androidx.core.os.CancellationSignal
import com.hihonor.android.facerecognition.FaceManager
import com.hihonor.android.facerecognition.HwFaceManagerFactory
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.engine.internal.face.hihonor.impl.HihonorFaceRecognizeManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper


class Hihonor3DFaceUnlockModule(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACE_HIHONOR3D) {
    private var hihonor3DFaceManager: FaceManager? = null

    init {
        ExecutorHelper.post {
            try {
                hihonor3DFaceManager = HwFaceManagerFactory.getFaceManager(context)
                d("$name.hihonor3DFaceManager - $hihonor3DFaceManager")
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
                hihonor3DFaceManager = null
            }

            listener?.initFinished(biometricMethod, this@Hihonor3DFaceUnlockModule)
        }
    }

    override val isUserAuthCanByUsedWithCrypto: Boolean
        get() = false

    override fun getManagers(): Set<Any> {
        val managers = HashSet<Any>()
        //pass only EMUI 10.1.0 manager
        hihonor3DFaceManager?.let {
            managers.add(it)
        }
        return managers
    }

    override val isManagerAccessible: Boolean
        get() = hihonor3DFaceManager != null
    override val isHardwarePresent: Boolean
        get() {
            try {
                if (hihonor3DFaceManager?.isHardwareDetected == true) return true
            } catch (e: Throwable) {

            }
            return false
        }

    override val hasEnrolled: Boolean
        get() {
            try {
                return hihonor3DFaceManager?.hasEnrolledTemplates() ?: false
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

        hihonor3DFaceManager?.let {
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
        hihonor3DFaceManager?.let {
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

                val crypto = if (biometricCryptoObject == null) null else {
                    if (biometricCryptoObject.cipher != null)
                        FaceManager.CryptoObject(biometricCryptoObject.cipher)
                    else if (biometricCryptoObject.mac != null)
                        FaceManager.CryptoObject(biometricCryptoObject.mac)
                    else if (biometricCryptoObject.signature != null)
                        FaceManager.CryptoObject(biometricCryptoObject.signature)
                    else
                        null
                }
                val callback = AuthCallback3DFace(
                    biometricCryptoObject,
                    restartPredicate,
                    cancellationSignal,
                    listener
                )

                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                d("$name.authenticate:  Crypto=$crypto")
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
    }

    private inner class AuthCallback3DFace(
        private val biometricCryptoObject: BiometricCryptoObject?,
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: CancellationSignal?,
        private val listener: AuthenticationListener?
    ) : FaceManager.AuthenticationCallback() {
        private var errorTs = 0L
        private val skipTimeout =
            context.resources.getInteger(android.R.integer.config_shortAnimTime)
        private var selfCanceled = false
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
            d("$name.onAuthenticationError: $errMsgId-$errString")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout)
                return
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (errMsgId) {
                HihonorFaceRecognizeManager.HIHONOR_FACE_AUTHENTICATOR_FAIL -> failureReason =
                    AuthenticationFailureReason.AUTHENTICATION_FAILED

                HihonorFaceRecognizeManager.HIHONOR_FACE_AUTH_ERROR_HW_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE

                HihonorFaceRecognizeManager.HIHONOR_FACE_AUTH_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT

                HihonorFaceRecognizeManager.HIHONOR_FACE_AUTH_ERROR_LOCKED -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }

                else -> {
                    if (!selfCanceled) {
                        listener?.onFailure(failureReason, tag())
                        postCancelTask {
                            if (cancellationSignal?.isCanceled == false) {
                                selfCanceled = true
                                listener?.onCanceled(tag())
                                Core.cancelAuthentication(this@Hihonor3DFaceUnlockModule)
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
                            Core.cancelAuthentication(this@Hihonor3DFaceUnlockModule)
                        }
                    }
                }
        }

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
            d("$name.onAuthenticationHelp: $helpMsgId-$helpString")
            listener?.onHelp(helpString)
        }

        override fun onAuthenticationSucceeded(result: FaceManager.AuthenticationResult?) {
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

        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            listener?.onFailure(AuthenticationFailureReason.AUTHENTICATION_FAILED, tag())
        }
    }
}