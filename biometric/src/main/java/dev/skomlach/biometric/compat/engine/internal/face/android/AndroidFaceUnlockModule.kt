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

package dev.skomlach.biometric.compat.engine.internal.face.android

import android.annotation.SuppressLint
import android.hardware.face.FaceAuthenticationManager
import android.hardware.face.FaceManager
import android.os.Build
import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationHelpReason
import dev.skomlach.biometric.compat.engine.*
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.CodeToString.getErrorCode
import dev.skomlach.biometric.compat.utils.CodeToString.getHelpCode
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper


class AndroidFaceUnlockModule @SuppressLint("WrongConstant") constructor(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACE_ANDROIDAPI) {
    private var faceAuthenticationManager: FaceAuthenticationManager? = null
    private var faceManager: FaceManager? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                faceAuthenticationManager = context.getSystemService(FaceAuthenticationManager::class.java)
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
            }
        }

        if(faceAuthenticationManager == null){
            try {
                faceAuthenticationManager =  context.getSystemService("face") as FaceAuthenticationManager?
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                faceManager = context.getSystemService(FaceManager::class.java)
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
            }
        }

        if(faceManager == null){
            try {
                faceManager = context.getSystemService("face") as FaceManager?
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
            }
        }
        listener?.initFinished(biometricMethod, this@AndroidFaceUnlockModule)
    }

    override fun getManagers(): Set<Any> {
        val managers = HashSet<Any>()
        faceManager?.let {
            managers.add(it)
        }
        faceAuthenticationManager?.let {
            managers.add(it)
        }
        return managers
    }

    override val isManagerAccessible: Boolean
        get() = faceAuthenticationManager != null || faceManager != null
    override val isHardwarePresent: Boolean
        get() {
            var faceAuthenticationManagerIsHardwareDetected = false
            var faceManagerIsHardwareDetected = false

            try {
                faceAuthenticationManagerIsHardwareDetected =
                    faceAuthenticationManager?.isHardwareDetected == true
            } catch (ignore: Throwable) {

            }


            try {
                faceManagerIsHardwareDetected = faceManager?.isHardwareDetected == true
            } catch (ignore: Throwable) {

            }

            return faceManagerIsHardwareDetected || faceAuthenticationManagerIsHardwareDetected
        }

    override fun hasEnrolled(): Boolean {

        try {
            faceAuthenticationManager?.javaClass?.methods?.firstOrNull { method ->
                method.name.startsWith(
                    "hasEnrolled"
                )
            }?.invoke(faceAuthenticationManager)?.let {
                if (it is Boolean)
                    return it
                else
                    throw RuntimeException("Unexpected type - $it")
            }
        } catch (ignore: Throwable) {

        }


        try {
            faceManager?.javaClass?.methods?.firstOrNull { method ->
                method.name.startsWith(
                    "hasEnrolled"
                )
            }?.invoke(faceManager)?.let {
                if (it is Boolean)
                    return it
                else
                    throw RuntimeException("Unexpected type - $it")
            }
        } catch (ignore: Throwable) {


        }


        e(RuntimeException("Unable to find 'hasEnrolled' method"))
        return false
    }

    @Throws(SecurityException::class)
    override fun authenticate(
        cancellationSignal: CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        d("$name.authenticate - $biometricMethod")
        // Why getCancellationSignalObject returns an Object is unexplained
        val signalObject =
            if (cancellationSignal == null) null else cancellationSignal.cancellationSignalObject as android.os.CancellationSignal?
        try {
            requireNotNull(signalObject) { "CancellationSignal cann't be null" }
            return
        } catch (e: Throwable) {
            e(e, "$name: authenticate failed unexpectedly")
            listener?.onFailure(AuthenticationFailureReason.UNKNOWN, tag())
        }
        if (faceAuthenticationManager?.isHardwareDetected == true && faceAuthenticationManager?.hasEnrolledFace() == true) {
            faceAuthenticationManager?.let {
                try {
                    // Occasionally, an NPE will bubble up out of FaceAuthenticationManager.authenticate
                    it.authenticate(
                        null, signalObject, 0,
                        FaceAuthenticationManagerAuthCallback(
                            restartPredicate,
                            cancellationSignal,
                            listener
                        ), ExecutorHelper.handler
                    )
                    return
                } catch (e: Throwable) {
                    e(e, "$name: authenticate failed unexpectedly")
                }
            }
        } else if (faceManager?.isHardwareDetected == true && faceManager?.hasEnrolledTemplates() == true) {
            faceManager?.let {
                try {
                    // Occasionally, an NPE will bubble up out of FaceAuthenticationManager.authenticate
                    it.authenticate(
                        null,
                        signalObject,
                        0,
                        FaceManagerAuthCallback(restartPredicate, cancellationSignal, listener),
                        ExecutorHelper.handler
                    )
                    return
                } catch (e: Throwable) {
                    e(e, "$name: authenticate failed unexpectedly")
                }
            }
        }
        listener?.onFailure(AuthenticationFailureReason.UNKNOWN, tag())
        return
    }

    internal inner class FaceManagerAuthCallback(
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: CancellationSignal?,
        private val listener: AuthenticationListener?
    ) : FaceManager.AuthenticationCallback() {
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
            d(name + ".onAuthenticationError: " + getErrorCode(errMsgId) + "-" + errString)
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (errMsgId) {
                BiometricCodes.BIOMETRIC_ERROR_NO_BIOMETRICS -> failureReason =
                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
                BiometricCodes.BIOMETRIC_ERROR_HW_NOT_PRESENT -> failureReason =
                    AuthenticationFailureReason.NO_HARDWARE
                BiometricCodes.BIOMETRIC_ERROR_HW_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                BiometricCodes.BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> {
                    BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(
                        biometricMethod.biometricType
                    )
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                }
                BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                BiometricCodes.BIOMETRIC_ERROR_NO_SPACE -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED
                BiometricCodes.BIOMETRIC_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT
                BiometricCodes.BIOMETRIC_ERROR_LOCKOUT -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }
                BiometricCodes.BIOMETRIC_ERROR_USER_CANCELED -> {
                    Core.cancelAuthentication(this@AndroidFaceUnlockModule)
                    return
                }
                BiometricCodes.BIOMETRIC_ERROR_CANCELED ->                     // Don't send a cancelled message.
                    return
            }
            if (restartCauseTimeout(failureReason)) {
                authenticate(cancellationSignal, listener, restartPredicate)
            } else
                if (restartPredicate?.invoke(failureReason) == true) {
                    listener?.onFailure(failureReason, tag())
                    authenticate(cancellationSignal, listener, restartPredicate)
                } else {
                    when (failureReason) {
                        AuthenticationFailureReason.SENSOR_FAILED, AuthenticationFailureReason.AUTHENTICATION_FAILED -> {
                            lockout()
                            failureReason = AuthenticationFailureReason.LOCKED_OUT
                        }
                    }
                    listener?.onFailure(failureReason, tag())
                }
        }

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
            d(name + ".onAuthenticationHelp: " + getHelpCode(helpMsgId) + "-" + helpString)
            listener?.onHelp(AuthenticationHelpReason.getByCode(helpMsgId), helpString)
        }

        override fun onAuthenticationSucceeded(result: FaceManager.AuthenticationResult?) {
            d("$name.onAuthenticationSucceeded: $result")
            listener?.onSuccess(tag())
        }

        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            listener?.onFailure(AuthenticationFailureReason.AUTHENTICATION_FAILED, tag())
        }
    }

    internal inner class FaceAuthenticationManagerAuthCallback(
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: CancellationSignal?,
        private val listener: AuthenticationListener?
    ) : FaceAuthenticationManager.AuthenticationCallback() {
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
            d(name + ".onAuthenticationError: " + getErrorCode(errMsgId) + "-" + errString)
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (errMsgId) {
                BiometricCodes.BIOMETRIC_ERROR_NO_BIOMETRICS -> failureReason =
                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
                BiometricCodes.BIOMETRIC_ERROR_HW_NOT_PRESENT -> failureReason =
                    AuthenticationFailureReason.NO_HARDWARE
                BiometricCodes.BIOMETRIC_ERROR_HW_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                BiometricCodes.BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> {
                    BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(
                        biometricMethod.biometricType
                    )
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                }
                BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                BiometricCodes.BIOMETRIC_ERROR_NO_SPACE -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED
                BiometricCodes.BIOMETRIC_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT
                BiometricCodes.BIOMETRIC_ERROR_LOCKOUT -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }
                BiometricCodes.BIOMETRIC_ERROR_USER_CANCELED -> {
                    Core.cancelAuthentication(this@AndroidFaceUnlockModule)
                    return
                }
                BiometricCodes.BIOMETRIC_ERROR_CANCELED ->                     // Don't send a cancelled message.
                    return
            }
            if (restartCauseTimeout(failureReason)) {
                authenticate(cancellationSignal, listener, restartPredicate)
            } else
                if (restartPredicate?.invoke(failureReason) == true) {
                    listener?.onFailure(failureReason, tag())
                    authenticate(cancellationSignal, listener, restartPredicate)
                } else {
                    when (failureReason) {
                        AuthenticationFailureReason.SENSOR_FAILED, AuthenticationFailureReason.AUTHENTICATION_FAILED -> {
                            lockout()
                            failureReason = AuthenticationFailureReason.LOCKED_OUT
                        }
                    }
                    listener?.onFailure(failureReason, tag())
                }
        }

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
            d(name + ".onAuthenticationHelp: " + getHelpCode(helpMsgId) + "-" + helpString)
            listener?.onHelp(AuthenticationHelpReason.getByCode(helpMsgId), helpString)
        }

        override fun onAuthenticationSucceeded(result: FaceAuthenticationManager.AuthenticationResult?) {
            d("$name.onAuthenticationSucceeded: $result")
            listener?.onSuccess(tag())
        }

        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            listener?.onFailure(AuthenticationFailureReason.AUTHENTICATION_FAILED, tag())
        }
    }

}