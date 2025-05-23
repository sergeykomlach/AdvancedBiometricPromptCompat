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

import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.engine.internal.face.hihonor.impl.HihonorFaceManager
import dev.skomlach.biometric.compat.engine.internal.face.hihonor.impl.HihonorFaceManagerFactory
import dev.skomlach.biometric.compat.engine.internal.face.hihonor.impl.HihonorFaceRecognizeManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper
import java.lang.ref.WeakReference


class HihonorFaceUnlockModule(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACE_HIHONOR) {
    //EMUI 10.1.0
    private var hihonorFaceManagerLegacy: HihonorFaceManager? = null
    private var viewWeakReference = WeakReference<SurfaceView?>(null)

    init {

        try {
            hihonorFaceManagerLegacy = HihonorFaceManagerFactory.getHihonorFaceManager()
            d("$name.hihonorFaceManagerLegacy - $hihonorFaceManagerLegacy")
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)
            hihonorFaceManagerLegacy = null
        }
        listener?.initFinished(biometricMethod, this@HihonorFaceUnlockModule)
    }

    override val isUserAuthCanByUsedWithCrypto: Boolean
        get() = false

    override fun getManagers(): Set<Any> {
        val managers = HashSet<Any>()
        hihonorFaceManagerLegacy?.let {
            managers.add(it)
        }
        return managers
    }

    override val isManagerAccessible: Boolean
        get() = hihonorFaceManagerLegacy != null
    override val isHardwarePresent: Boolean
        get() {
            try {
                if (hihonorFaceManagerLegacy?.isHardwareDetected == true) return true
            } catch (e: Throwable) {

            }

            return false
        }

    override val hasEnrolled: Boolean
        get() {
            try {
                if (hihonorFaceManagerLegacy?.hasEnrolledTemplates() == true) return true
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
        hihonorFaceManagerLegacy?.let {
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
        listener?.onFailure(tag(), AuthenticationFailureReason.INTERNAL_ERROR, "Can't start authenticate for $name")
        return
    }

    private fun authenticateInternal(
        biometricCryptoObject: BiometricCryptoObject?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        d("$name.authenticate - $biometricMethod; Crypto=$biometricCryptoObject")
        hihonorFaceManagerLegacy?.let {
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


                val callback = AuthCallbackLegacy(
                    biometricCryptoObject,
                    restartPredicate,
                    cancellationSignal,
                    listener
                )
                signalObject.setOnCancelListener {
                    it.cancel()
                }
                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                viewWeakReference.get()?.let { view ->
                    if (view.visibility == View.VISIBLE || view.holder.isCreating) {
                        authCallTimestamp.set(System.currentTimeMillis())
                        it.authenticate(callback, view.holder.surface)
                        return
                    } else {
                        view.holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(p0: SurfaceHolder) {
                                authCallTimestamp.set(System.currentTimeMillis())
                                it.authenticate(callback, view.holder.surface)
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

                } ?: kotlin.run {
                    authCallTimestamp.set(System.currentTimeMillis())
                    it.authenticate(callback, null)
                }

                return


            } catch (e: Throwable) {
                e(e, "$name: authenticate failed unexpectedly")
            }
        }
        listener?.onFailure(tag(), AuthenticationFailureReason.INTERNAL_ERROR, "Can't start authenticate for $name")
    }

    private inner class AuthCallbackLegacy(
        private val biometricCryptoObject: BiometricCryptoObject?,
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: CancellationSignal?,
        private val listener: AuthenticationListener?
    ) : HihonorFaceManager.AuthenticatorCallback() {
        private var errorTs = 0L
        private val skipTimeout =
            context.resources.getInteger(android.R.integer.config_shortAnimTime)
        private var selfCanceled = false
        override fun onAuthenticationError(errMsgId: Int) {
            d("$name.onAuthenticationError: $errMsgId")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout)
                return
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (if (errMsgId < 1000) errMsgId else errMsgId % 1000) {
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
                        listener?.onFailure(tag(), failureReason, "$name - $errMsgId")
                        postCancelTask {
                            if (cancellationSignal?.isCanceled == false) {
                                selfCanceled = true
                                listener?.onCanceled(
                                    tag(),
                                    AuthenticationFailureReason.CANCELED,
                                    null
                                )
                                Core.cancelAuthentication(this@HihonorFaceUnlockModule)
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
                    listener?.onFailure(tag(), failureReason, null)
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
                    listener?.onFailure(tag(), failureReason, null)
                    postCancelTask {
                        if (cancellationSignal?.isCanceled == false) {
                            selfCanceled = true
                            listener?.onCanceled(tag(), AuthenticationFailureReason.CANCELED, null)
                            Core.cancelAuthentication(this@HihonorFaceUnlockModule)
                        }
                    }
                }
        }

        override fun onAuthenticationStatus(helpMsgId: Int) {
            d("$name.onAuthenticationHelp: $helpMsgId")
            listener?.onHelp(null)
        }

        override fun onAuthenticationSucceeded() {
            d("$name.onAuthenticationSucceeded: ")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return
            errorTs = tmp
            listener?.onSuccess(
                tag(),
                BiometricCryptoObject(
                    biometricCryptoObject?.signature,
                    biometricCryptoObject?.cipher,
                    biometricCryptoObject?.mac
                )
            )
        }

        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            listener?.onFailure(tag(), AuthenticationFailureReason.AUTHENTICATION_FAILED, null)
        }
    }
}