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

package dev.skomlach.biometric.compat.engine.internal.face.huawei

import androidx.core.os.CancellationSignal
import com.huawei.facerecognition.FaceManager
import com.huawei.facerecognition.HwFaceManagerFactory
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.engine.internal.face.huawei.impl.HuaweiFaceRecognizeManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper


class Huawei3DFaceUnlockModule(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACE_HUAWEI3D) {
    private var huawei3DFaceManager: FaceManager? = null

    init {
        ExecutorHelper.post {
            try {
                huawei3DFaceManager = HwFaceManagerFactory.getFaceManager(context)
                d("$name.huawei3DFaceManager - $huawei3DFaceManager")
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
                huawei3DFaceManager = null
            }

            listener?.initFinished(biometricMethod, this@Huawei3DFaceUnlockModule)
        }
    }

    override val isUserAuthCanByUsedWithCrypto: Boolean
        get() = false

    override fun getManagers(): Set<Any> {
        val managers = HashSet<Any>()
        //pass only EMUI 10.1.0 manager
        huawei3DFaceManager?.let {
            managers.add(it)
        }
        return managers
    }

    override val isManagerAccessible: Boolean
        get() = huawei3DFaceManager != null
    override val isHardwarePresent: Boolean
        get() {
            try {
                if (huawei3DFaceManager?.isHardwareDetected == true) return true
            } catch (e: Throwable) {

            }
            return false
        }

    override val hasEnrolled: Boolean
        get() {
            try {
                return huawei3DFaceManager?.hasEnrolledTemplates() ?: false
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
        huawei3DFaceManager?.let {
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
        huawei3DFaceManager?.let {
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
        private val cancellationSignal: androidx.core.os.CancellationSignal?,
        private val listener: AuthenticationListener?
    ) : FaceManager.AuthenticationCallback() {
        private var errorTs = System.currentTimeMillis()
        private val skipTimeout =
            context.resources.getInteger(android.R.integer.config_shortAnimTime)

        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
            d("$name.onAuthenticationError: $errMsgId-$errString")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (errMsgId) {
                HuaweiFaceRecognizeManager.HUAWEI_FACE_AUTHENTICATOR_FAIL -> failureReason =
                    AuthenticationFailureReason.AUTHENTICATION_FAILED

                HuaweiFaceRecognizeManager.HUAWEI_FACE_AUTH_ERROR_HW_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE

                HuaweiFaceRecognizeManager.HUAWEI_FACE_AUTH_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT

                HuaweiFaceRecognizeManager.HUAWEI_FACE_AUTH_ERROR_LOCKED -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }

                else -> {
                    //no-op
                }
            }
            if (restartCauseTimeout(failureReason)) {
                cancellationSignal?.cancel()
                ExecutorHelper.postDelayed({
                    authenticateInternal(biometricCryptoObject, listener, restartPredicate)
                }, context.resources.getInteger(android.R.integer.config_longAnimTime).toLong())
            } else
                if (failureReason == AuthenticationFailureReason.TIMEOUT || restartPredicate?.invoke(
                        failureReason
                    ) == true
                ) {
                    listener?.onFailure(failureReason, tag())
                    cancellationSignal?.cancel()
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
                    if (mutableListOf(
                            AuthenticationFailureReason.SENSOR_FAILED,
                            AuthenticationFailureReason.AUTHENTICATION_FAILED
                        ).contains(failureReason)
                    ) {
                        cancellationSignal?.cancel()
                        ExecutorHelper.postDelayed({
                            authenticateInternal(biometricCryptoObject, listener, restartPredicate)
                        }, context.resources.getInteger(android.R.integer.config_longAnimTime).toLong())
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