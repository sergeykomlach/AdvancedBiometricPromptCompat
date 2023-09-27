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

package dev.skomlach.biometric.compat.engine.internal.face.oppo

import android.annotation.SuppressLint
import android.hardware.biometrics.CryptoObject
import android.hardware.face.OppoMirrorFaceManager
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


class OppoFaceUnlockModule @SuppressLint("WrongConstant") constructor(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACE_OPPO) {
    companion object {
        const val BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL = 14
        const val FACE_ACQUIRED_CAMERA_PREVIEW = 1001
        const val FACE_ACQUIRED_DEPTH_TOO_NEARLY = 303
        const val FACE_ACQUIRED_DOE_CHECK = 307
        const val FACE_ACQUIRED_DOE_PRECHECK = 306
        const val FACE_ACQUIRED_FACEDOE_IMAGE_READY = 308
        const val FACE_ACQUIRED_FACE_OBSCURED = 19
        const val FACE_ACQUIRED_GOOD = 0
        const val FACE_ACQUIRED_HACKER = 104
        const val FACE_ACQUIRED_INSUFFICIENT = 1
        const val FACE_ACQUIRED_IR_HACKER = 305
        const val FACE_ACQUIRED_IR_PATTERN = 304
        const val FACE_ACQUIRED_MOUTH_OCCLUSION = 113
        const val FACE_ACQUIRED_MULTI_FACE = 116
        const val FACE_ACQUIRED_NOSE_OCCLUSION = 115
        const val FACE_ACQUIRED_NOT_DETECTED = 11
        const val FACE_ACQUIRED_NOT_FRONTAL_FACE = 114
        const val FACE_ACQUIRED_NO_FACE = 101
        const val FACE_ACQUIRED_NO_FOCUS = 112
        const val FACE_ACQUIRED_PAN_TOO_EXTREME = 16
        const val FACE_ACQUIRED_POOR_GAZE = 10
        const val FACE_ACQUIRED_RECALIBRATE = 13
        const val FACE_ACQUIRED_ROLL_TOO_EXTREME = 18
        const val FACE_ACQUIRED_SENSOR_DIRTY = 21
        const val FACE_ACQUIRED_START = 20
        const val FACE_ACQUIRED_SWITCH_DEPTH = 302
        const val FACE_ACQUIRED_SWITCH_IR = 301
        const val FACE_ACQUIRED_TILT_TOO_EXTREME = 17
        const val FACE_ACQUIRED_TOO_BRIGHT = 2
        const val FACE_ACQUIRED_TOO_CLOSE = 4
        const val FACE_ACQUIRED_TOO_DARK = 3
        const val FACE_ACQUIRED_TOO_DIFFERENT = 14
        const val FACE_ACQUIRED_TOO_FAR = 5
        const val FACE_ACQUIRED_TOO_HIGH = 6
        const val FACE_ACQUIRED_TOO_LEFT = 9
        const val FACE_ACQUIRED_TOO_LOW = 7
        const val FACE_ACQUIRED_TOO_MUCH_MOTION = 12
        const val FACE_ACQUIRED_TOO_RIGHT = 8
        const val FACE_ACQUIRED_TOO_SIMILAR = 15
        const val FACE_ACQUIRED_VENDOR = 22
        const val FACE_ACQUIRED_VENDOR_BASE = 1000
        const val FACE_AUTHENTICATE_AUTO = 0
        const val FACE_AUTHENTICATE_BY_FINGERPRINT = 3
        const val FACE_AUTHENTICATE_BY_USER = 1
        const val FACE_AUTHENTICATE_BY_USER_WITH_ANIM = 2
        const val FACE_AUTHENTICATE_PAY = 4
        const val FACE_ERROR_CAMERA_UNAVAILABLE = 0
        const val FACE_ERROR_CANCELED = 5
        const val FACE_ERROR_HW_NOT_PRESENT = 12
        const val FACE_ERROR_HW_UNAVAILABLE = 1
        const val FACE_ERROR_LOCKOUT = 7
        const val FACE_ERROR_LOCKOUT_PERMANENT = 9
        const val FACE_ERROR_NEGATIVE_BUTTON = 13
        const val FACE_ERROR_NOT_ENROLLED = 11
        const val FACE_ERROR_NO_SPACE = 4
        const val FACE_ERROR_TIMEOUT = 3
        const val FACE_ERROR_UNABLE_TO_PROCESS = 2
        const val FACE_ERROR_UNABLE_TO_REMOVE = 6
        const val FACE_ERROR_USER_CANCELED = 10
        const val FACE_ERROR_VENDOR = 8
        const val FACE_ERROR_VENDOR_BASE = 1000
        const val FACE_KEYGUARD_CANCELED_BY_SCREEN_OFF = "cancelRecognitionByScreenOff"
        const val FACE_WITH_EYES_CLOSED = 111
        const val FEATURE_REQUIRE_ATTENTION = 1
        const val FEATURE_REQUIRE_REQUIRE_DIVERSITY = 2
    }

    private var manager: OppoMirrorFaceManager? = null

    init {


        try {
            manager = ContextCompat.getSystemService(context, OppoMirrorFaceManager::class.java)
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)
        }


        if (manager == null) {
            try {
                manager = context.getSystemService("face") as OppoMirrorFaceManager?
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
            }
        }


        listener?.initFinished(biometricMethod, this@OppoFaceUnlockModule)
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
                return manager?.hasEnrolledTemplates() ?: false
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
                val callback: OppoMirrorFaceManager.AuthenticationCallback =
                    AuthCallback(
                        biometricCryptoObject,
                        restartPredicate,
                        cancellationSignal,
                        listener
                    )

                // Occasionally, an NPE will bubble up out of SomeManager.authenticate
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
    ) : OppoMirrorFaceManager.AuthenticationCallback() {
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
                FACE_ERROR_NOT_ENROLLED -> failureReason =
                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED

                FACE_ERROR_HW_NOT_PRESENT -> failureReason =
                    AuthenticationFailureReason.NO_HARDWARE

                FACE_ERROR_HW_UNAVAILABLE, FACE_ERROR_CAMERA_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE

                FACE_ERROR_LOCKOUT_PERMANENT -> {
                    BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(
                        biometricMethod.biometricType
                    )
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                }

                FACE_ERROR_UNABLE_TO_PROCESS, FACE_ERROR_NO_SPACE -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED

                FACE_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT

                FACE_ERROR_LOCKOUT -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }

                FACE_ERROR_CANCELED, FACE_ERROR_NEGATIVE_BUTTON, FACE_ERROR_USER_CANCELED -> {
                    if (!selfCanceled) {
                        Core.cancelAuthentication(this@OppoFaceUnlockModule)
                        listener?.onCanceled(tag())
                    }
                    return
                }

                else -> {
                    if (!selfCanceled) {
                        listener?.onFailure(failureReason, tag())
                        ExecutorHelper.postDelayed({
                            selfCanceled = true
                            Core.cancelAuthentication(this@OppoFaceUnlockModule)
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
                        Core.cancelAuthentication(this@OppoFaceUnlockModule)
                        listener?.onCanceled(tag())
                    }, 2000)
                }
        }

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
            d("$name.onAuthenticationHelp: $helpMsgId-$helpString")
            listener?.onHelp(helpString)
        }

        override fun onAuthenticationSucceeded(result: OppoMirrorFaceManager.AuthenticationResult?) {
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
                    Core.cancelAuthentication(this@OppoFaceUnlockModule)
                    listener?.onCanceled(tag())
                }, 2000)
            }
        }
    }

}