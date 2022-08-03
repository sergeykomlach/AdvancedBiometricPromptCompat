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

package dev.skomlach.biometric.compat.engine.internal.face.miui

import android.annotation.SuppressLint
import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.IMiuiFaceManager
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.MiuiFaceFactory
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.Miuiface
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.TimeUnit


class MiuiFaceUnlockModule @SuppressLint("WrongConstant") constructor(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACE_MIUI) {
    private var manager: IMiuiFaceManager? = null

    init {

        try {
            manager = MiuiFaceFactory.getFaceManager(MiuiFaceFactory.TYPE_3D)
            if (manager?.isFaceFeatureSupport == false) {
                throw RuntimeException("Miui 3DFace not supported")
            }
            manager?.isFaceFeatureSupport
            manager?.enrolledFaces
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)
            try {
                manager = MiuiFaceFactory.getFaceManager(MiuiFaceFactory.TYPE_2D)
                if (manager?.isFaceFeatureSupport == false) {
                    throw RuntimeException("Miui 2DFace not supported")
                }
                manager?.isFaceFeatureSupport
                manager?.enrolledFaces
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
                manager = null
            }
        }
        e("MiuiFaceUnlockModule - $manager")

        listener?.initFinished(biometricMethod, this@MiuiFaceUnlockModule)
    }

    override val isUserAuthCanByUsedWithCrypto: Boolean
        get() = false

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
                return manager?.isFaceFeatureSupport == true
            } catch (e: Throwable) {
                e(e, name)
            }

            return false
        }

    override fun hasEnrolled(): Boolean {

        try {
            return manager?.isFaceFeatureSupport == true && manager?.enrolledFaces?.isNotEmpty() == true
        } catch (e: Throwable) {
            e(e, name)
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
        d("$name.authenticate - $biometricMethod; Crypto=$biometricCryptoObject")
        manager?.let {
            try {
                val callback: IMiuiFaceManager.AuthenticationCallback =
                    AuthCallback(
                        biometricCryptoObject,
                        restartPredicate,
                        cancellationSignal,
                        listener
                    )

                // Why getCancellationSignalObject returns an Object is unexplained
                val signalObject =
                    (if (cancellationSignal == null) null else cancellationSignal.cancellationSignalObject as android.os.CancellationSignal?)
                        ?: throw IllegalArgumentException("CancellationSignal cann't be null")
                if (!it.isFaceUnlockInited)
                    it.preInitAuthen()
                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                it.authenticate(
                    signalObject,
                    0,
                    callback,
                    ExecutorHelper.handler,
                    TimeUnit.SECONDS.toMillis(30)
                        .toInt()
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
    ) : IMiuiFaceManager.AuthenticationCallback() {
        private var errorTs = System.currentTimeMillis()
        private val skipTimeout = context.resources.getInteger(android.R.integer.config_shortAnimTime)
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
            d("$name.onAuthenticationError: $errMsgId-$errString")
            val tmp = System.currentTimeMillis()
            if(tmp - errorTs <= skipTimeout)
                return
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.UNKNOWN

            //See IMiuiFaceManagerImpl.getMessageInfo()
            when (errMsgId) {
                11 -> failureReason =
                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
                12 -> failureReason =
                    AuthenticationFailureReason.NO_HARDWARE
                1 -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                9 -> {
                    BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(
                        biometricMethod.biometricType
                    )
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                }
                2 -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                4 -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED
                3 -> failureReason =
                    AuthenticationFailureReason.TIMEOUT
                7 -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }
                else -> {
                    Core.cancelAuthentication(this@MiuiFaceUnlockModule)
                    listener?.onCanceled(tag())
                    return
                }
            }
            if (restartCauseTimeout(failureReason)) {
                authenticate(biometricCryptoObject, cancellationSignal, listener, restartPredicate)
            } else
                if (failureReason == AuthenticationFailureReason.TIMEOUT || restartPredicate?.invoke(
                        failureReason
                    ) == true
                ) {
                    listener?.onFailure(failureReason, tag())
                    authenticate(
                        biometricCryptoObject,
                        cancellationSignal,
                        listener,
                        restartPredicate
                    )
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
                    if (manager?.isReleased == false) manager?.release()
                }
        }

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
            d("$name.onAuthenticationHelp: $helpMsgId-$helpString")
            listener?.onHelp(helpString)
        }

        override fun onAuthenticationSucceeded(miuiface: Miuiface?) {
            d("$name.onAuthenticationSucceeded: $miuiface")
            listener?.onSuccess(
                tag(),
                BiometricCryptoObject(
                    biometricCryptoObject?.signature,
                    biometricCryptoObject?.cipher,
                    biometricCryptoObject?.mac
                )
            )
            if (manager?.isReleased == false) manager?.release()
        }

        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            //NOTE: unlike other API's, MIUI call this one only for TIMEOUT
            listener?.onFailure(AuthenticationFailureReason.TIMEOUT, tag())
            authenticate(biometricCryptoObject, cancellationSignal, listener, restartPredicate)
        }
    }

}