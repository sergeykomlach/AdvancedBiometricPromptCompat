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

package dev.skomlach.biometric.compat.engine.internal.face.samsung

import android.annotation.SuppressLint
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.core.os.CancellationSignal
import com.samsung.android.bio.face.SemBioFaceManager
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
import java.lang.ref.WeakReference


class SamsungFaceUnlockModule @SuppressLint("WrongConstant") constructor(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACE_SAMSUNG) {
    companion object {
        const val FACE_ACQUIRED_FAKE = 4
        const val FACE_ACQUIRED_GOOD = 0
        const val FACE_ACQUIRED_INVALID = 2
        const val FACE_ACQUIRED_LOW_QUALITY = 3
        const val FACE_ACQUIRED_MISALIGNED = 7
        const val FACE_ACQUIRED_PROCESS_FAIL = 1
        const val FACE_ACQUIRED_TOO_BIG = 5
        const val FACE_ACQUIRED_TOO_SMALL = 6
        const val FACE_ERROR_CAMERA_FAILURE = 10003
        const val FACE_ERROR_CAMERA_UNAVAILABLE = 10005
        const val FACE_ERROR_CANCELED = 5
        const val FACE_ERROR_HW_UNAVAILABLE = 1
        const val FACE_ERROR_IDENTIFY_FAILURE_BROKEN_DATABASE = 1004
        const val FACE_ERROR_LOCKOUT = 10001
        const val FACE_ERROR_NO_SPACE = 4
        const val FACE_ERROR_TEMPLATE_CORRUPTED = 1004
        const val FACE_ERROR_TIMEOUT = 3
        const val FACE_ERROR_UNABLE_TO_PROCESS = 2
        const val FACE_ERROR_FACE_NOT_RECOGNIZED = 1006
        const val FACE_OK = 0
    }

    private var manager: SemBioFaceManager? = null
    private var viewWeakReference = WeakReference<SurfaceView?>(null)
    init {
        manager = try {
            SemBioFaceManager.getInstance(context)
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)
            null
        }

        listener?.initFinished(biometricMethod, this@SamsungFaceUnlockModule)
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
                return manager?.hasEnrolledFaces() == true
            } catch (e: Throwable) {

            }

            return false
        }

    fun setCallerView(targetView: SurfaceView?) {
        d("$name.setCallerView: $targetView")
        viewWeakReference = WeakReference(targetView)
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

                val callback: SemBioFaceManager.AuthenticationCallback =
                    AuthCallback(
                        biometricCryptoObject,
                        restartPredicate,
                        cancellationSignal,
                        listener
                    )

                // Occasionally, an NPE will bubble up out of SemBioSomeManager.authenticate
                val crypto = if (biometricCryptoObject == null) null else {

                    if (biometricCryptoObject.cipher != null)
                        SemBioFaceManager.CryptoObject(biometricCryptoObject.cipher, null)
                    else if (biometricCryptoObject.mac != null)
                        SemBioFaceManager.CryptoObject(biometricCryptoObject.mac, null)
                    else if (biometricCryptoObject.signature != null)
                        SemBioFaceManager.CryptoObject(biometricCryptoObject.signature, null)
                    else
                        null
                }

                d("$name.authenticate:  Crypto=$crypto")
                viewWeakReference.get()?.let { view ->
                    if (view.visibility == View.VISIBLE || view.holder.isCreating) {
                        authCallTimestamp.set(System.currentTimeMillis())
                        it.authenticate(
                            crypto,
                            signalObject,
                            0,
                            callback,
                            ExecutorHelper.handler,
                            view
                        )
                        return
                    } else {
                        view.holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(p0: SurfaceHolder) {
                                authCallTimestamp.set(System.currentTimeMillis())
                                it.authenticate(
                                    crypto,
                                    signalObject,
                                    0,
                                    callback,
                                    ExecutorHelper.handler,
                                    view
                                )
                            }

                            override fun surfaceChanged(
                                p0: SurfaceHolder,
                                p1: Int,
                                p2: Int,
                                p3: Int
                            ) {

                            }

                            override fun surfaceDestroyed(p0: SurfaceHolder) {

                            }
                        })
                        view.visibility = View.VISIBLE
                    }
                } ?: run {
                    authCallTimestamp.set(System.currentTimeMillis())
                    it.authenticate(
                        crypto,
                        signalObject,
                        0,
                        callback,
                        ExecutorHelper.handler,
                        null
                    )
                }
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
    ) : SemBioFaceManager.AuthenticationCallback() {
        private var errorTs = System.currentTimeMillis()
        private val skipTimeout =
            context.resources.getInteger(android.R.integer.config_shortAnimTime)
        private var selfCanceled = false
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
            d("$name.onAuthenticationError: $errMsgId-$errString")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (errMsgId) {
                FACE_ERROR_HW_UNAVAILABLE, FACE_ERROR_CAMERA_UNAVAILABLE, FACE_ERROR_IDENTIFY_FAILURE_BROKEN_DATABASE, FACE_ERROR_CAMERA_FAILURE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE

                FACE_ERROR_UNABLE_TO_PROCESS -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED

                FACE_ERROR_NO_SPACE -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED

                FACE_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT

                FACE_ERROR_LOCKOUT -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }

                FACE_ERROR_CANCELED -> {
                    if (!selfCanceled) {
                        Core.cancelAuthentication(this@SamsungFaceUnlockModule)
                        listener?.onCanceled(tag())
                    }
                    return
                }

                FACE_ERROR_FACE_NOT_RECOGNIZED -> {
                    failureReason =
                        AuthenticationFailureReason.AUTHENTICATION_FAILED
                }

                else -> {
                    if (!selfCanceled) {
                        listener?.onFailure(failureReason, tag())
                        ExecutorHelper.postDelayed({
                            selfCanceled = true
                            Core.cancelAuthentication(this@SamsungFaceUnlockModule)
                            listener?.onCanceled(tag())
                        }, 2000)
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
                    ExecutorHelper.postDelayed({
                        selfCanceled = true
                        Core.cancelAuthentication(this@SamsungFaceUnlockModule)
                        listener?.onCanceled(tag())
                    }, 2000)
                }
        }

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
            d("$name.onAuthenticationHelp: $helpMsgId-$helpString")
            listener?.onHelp(helpString)
        }

        override fun onAuthenticationSucceeded(result: SemBioFaceManager.AuthenticationResult?) {
            d("$name.onAuthenticationSucceeded: $result; Crypto=${result?.cryptoObject}")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return
            errorTs = tmp
            listener?.onSuccess(
                tag(),
                BiometricCryptoObject(
                    result?.cryptoObject?.getSignature(),
                    result?.cryptoObject?.getCipher(),
                    result?.cryptoObject?.getMac()
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
                ExecutorHelper.postDelayed({
                    selfCanceled = true
                    Core.cancelAuthentication(this@SamsungFaceUnlockModule)
                    listener?.onCanceled(tag())
                }, 2000)
            }
        }
    }

}