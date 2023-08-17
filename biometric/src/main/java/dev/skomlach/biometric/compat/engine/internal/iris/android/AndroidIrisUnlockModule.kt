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

package dev.skomlach.biometric.compat.engine.internal.iris.android

import android.annotation.SuppressLint
import android.hardware.biometrics.CryptoObject
import android.hardware.iris.IrisManager
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


class AndroidIrisUnlockModule @SuppressLint("WrongConstant") constructor(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.IRIS_ANDROIDAPI) {
    companion object {
        /**
         * The hardware is unavailable. Try again later.
         */
        const val IRIS_ERROR_HW_UNAVAILABLE = 1

        /**
         * Error state returned when the sensor was unable to process the current image.
         */
        const val IRIS_ERROR_UNABLE_TO_PROCESS = 2

        /**
         * Error state returned when the current request has been running too long. This is intended to
         * prevent programs from waiting for the iris sensor indefinitely. The timeout is
         * platform and sensor-specific, but is generally on the order of 30 seconds.
         */
        const val IRIS_ERROR_TIMEOUT = 3

        /**
         * Error state returned for operations like enrollment; the operation cannot be completed
         * because there's not enough storage remaining to complete the operation.
         */
        const val IRIS_ERROR_NO_SPACE = 4

        /**
         * The operation was canceled because the iris sensor is unavailable. For example,
         * this may happen when the user is switched, the device is locked or another pending operation
         * prevents or disables it.
         */
        const val IRIS_ERROR_CANCELED = 5

        /**
         * The [IrisManager.remove] call failed. Typically this will happen when the
         * provided iris id was incorrect.
         *
         * @hide
         */
        const val IRIS_ERROR_UNABLE_TO_REMOVE = 6

        /**
         * The operation was canceled because the API is locked out due to too many attempts.
         * This occurs after 5 failed attempts, and lasts for 30 seconds.
         */
        const val IRIS_ERROR_LOCKOUT = 7

        /**
         * Hardware vendors may extend this list if there are conditions that do not fall under one of
         * the above categories. Vendors are responsible for providing error strings for these errors.
         * These messages are typically reserved for internal operations such as enrollment, but may be
         * used to express vendor errors not covered by the ones in iris.h. Applications are
         * expected to show the error message string if they happen, but are advised not to rely on the
         * message id since they will be device and vendor-specific
         */
        const val IRIS_ERROR_VENDOR = 8

        /**
         * The operation was canceled because IRIS_ERROR_LOCKOUT occurred too many times.
         * Iris authentication is disabled until the user unlocks with strong authentication
         * (PIN/Pattern/Password)
         */
        const val IRIS_ERROR_LOCKOUT_PERMANENT = 9

        /**
         * The user canceled the operation. Upon receiving this, applications should use alternate
         * authentication (e.g. a password). The application should also provide the means to return
         * to iris authentication, such as a "use iris" button.
         */
        const val IRIS_ERROR_USER_CANCELED = 10

        /**
         * @hide
         */
        const val IRIS_ERROR_VENDOR_BASE = 1000
        //
        // Image acquisition messages. Must agree with those in iris.h
        //
        //
        // Image acquisition messages. Must agree with those in iris.h
        //
        /**
         * The image acquired was good.
         */
        const val IRIS_ACQUIRED_GOOD = 0

        /**
         * The iris image was not good enough to process due to a detected condition (i.e. no iris) or
         * a possibly (See [or @link #IRIS_ACQUIRED_TOO_DARK][.IRIS_ACQUIRED_TOO_BRIGHT]).
         */
        const val IRIS_ACQUIRED_INSUFFICIENT = 1

        /**
         * The iris image was too bright due to too much ambient light.
         * For example, it's reasonable return this after multiple
         * [.IRIS_ACQUIRED_INSUFFICIENT]
         * The user is expected to take action to re try in better lighting conditions
         * when this is returned.
         */
        const val IRIS_ACQUIRED_TOO_BRIGHT = 2

        /**
         * The iris image was too dark due to illumination light obscured.
         * For example, it's reasonable return this after multiple
         * [.IRIS_ACQUIRED_INSUFFICIENT]
         * The user is expected to take action to uncover illumination light source
         * when this is returned.
         */
        const val IRIS_ACQUIRED_TOO_DARK = 3

        /**
         * The iris was not in field of view. User might be close to the camera and should be
         * informed on what needs to happen to resolve this problem, e.g. "move further."
         */
        const val IRIS_ACQUIRED_TOO_CLOSE = 4

        /**
         * The iris image was not enough. User might be far from the camera and should be
         * informed on what needs to happen to resolve this problem, e.g. "move closer."
         */
        const val IRIS_ACQUIRED_TOO_FAR = 5

        /**
         * The iris image was not available. User might have been closing the eyes and should be
         * informed on what needs to happen to resolve this problem, e.g. "open eyes."
         */
        const val IRIS_ACQUIRED_EYES_CLOSED = 6

        /**
         * The iris image was not enough. User might have been closing the eyes and should be
         * informed on what needs to happen to resolve this problem, e.g. "open eyes wider."
         */
        const val IRIS_ACQUIRED_EYES_PARTIALLY_OBSCURED = 7

        /**
         * The image acquired was having one iris.
         */
        const val IRIS_ACQUIRED_DETECTED_ONE_EYE = 8

        /**
         * The image acquired was having two iris.
         */
        const val IRIS_ACQUIRED_DETECTED_TWO_EYE = 9

        /**
         * The image acquired was having too many irises(i.e. someone else in the view, reflections, etc.).
         */
        const val IRIS_ACQUIRED_DETECTED_TOO_MANY_EYES = 10

        /**
         * Hardware vendors may extend this list if there are conditions that do not fall under one of
         * the above categories. Vendors are responsible for providing error strings for these errors.
         * @hide
         */
        const val IRIS_ACQUIRED_VENDOR = 11

        /**
         * @hide
         */
        const val IRIS_ACQUIRED_VENDOR_BASE = 1000
    }

    private var manager: IrisManager? = null

    init {

        try {
            manager = ContextCompat.getSystemService(context, IrisManager::class.java)
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)
        }


        if (manager == null) {
            try {
                manager = context.getSystemService("iris") as IrisManager?
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
            }
        }
        listener?.initFinished(biometricMethod, this@AndroidIrisUnlockModule)
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
                return manager?.hasEnrolledIrises() ?: false
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
        d("$name.authenticate - $biometricMethod; Crypto=$biometricCryptoObject")
        manager?.let {
            try {
                val callback: IrisManager.AuthenticationCallback =
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

                // Occasionally, an NPE will bubble up out of IrisManager.authenticate
                val crypto = if (biometricCryptoObject == null) null else {
                    if (biometricCryptoObject.cipher != null)
                        CryptoObject(biometricCryptoObject.cipher)
                    else if (biometricCryptoObject.mac != null)
                        CryptoObject(biometricCryptoObject.mac)
                    else if (biometricCryptoObject.signature != null)
                        CryptoObject(biometricCryptoObject.signature)
                    else
                        null
                }
                d("$name.authenticate:  Crypto=$crypto")
                try {
                    authCallTimestamp.set(System.currentTimeMillis())
                    it.authenticate(
                        crypto,
                        signalObject,
                        callback,
                        ExecutorHelper.handler,
                        0
                    )
                    return
                } catch (e: SecurityException) {
                } catch (e: NoSuchMethodError) {
                }
                try {
                    authCallTimestamp.set(System.currentTimeMillis())
                    it.authenticate(
                        crypto,
                        signalObject,
                        callback,
                        ExecutorHelper.handler,
                        0,
                        true
                    )
                    return
                } catch (e: SecurityException) {
                } catch (e: NoSuchMethodError) {
                }
                try {
                    authCallTimestamp.set(System.currentTimeMillis())
                    it.authenticate(
                        crypto,
                        signalObject,
                        0,
                        callback,
                        ExecutorHelper.handler
                    )
                    return
                } catch (e: SecurityException) {
                } catch (e: NoSuchMethodError) {
                }
                authCallTimestamp.set(System.currentTimeMillis())
                it.authenticate(
                    crypto,
                    signalObject,
                    0,
                    callback,
                    ExecutorHelper.handler,
                    0
                )
                return
            } catch (e: Throwable) {
                e(e, "$name: authenticate failed unexpectedly")
                listener?.onFailure(AuthenticationFailureReason.HARDWARE_UNAVAILABLE, tag())
                return
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
    ) : IrisManager.AuthenticationCallback() {
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
                IRIS_ERROR_HW_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                IRIS_ERROR_LOCKOUT_PERMANENT -> {
                    BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(
                        biometricMethod.biometricType
                    )
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                }
                IRIS_ERROR_UNABLE_TO_PROCESS -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                IRIS_ERROR_NO_SPACE -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED
                IRIS_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT
                IRIS_ERROR_LOCKOUT -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }
                else -> {
                    Core.cancelAuthentication(this@AndroidIrisUnlockModule)
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

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
            d("$name.onAuthenticationHelp: $helpMsgId-$helpString")
            listener?.onHelp(helpString)
        }

        override fun onAuthenticationSucceeded(result: IrisManager.AuthenticationResult?) {
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