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
import androidx.annotation.RestrictTo
import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.AuthenticationHelpReason.Companion.getByCode
import dev.skomlach.biometric.compat.engine.BiometricCodes
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.engine.internal.core.Core
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.CodeToString.getErrorCode
import dev.skomlach.biometric.compat.utils.CodeToString.getHelpCode
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper
import me.weishu.reflection.Reflection

@RestrictTo(RestrictTo.Scope.LIBRARY)
class AndroidFaceUnlockModule @SuppressLint("WrongConstant") constructor(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACE_ANDROIDAPI) {
    private var faceAuthenticationManager: FaceAuthenticationManager? = null
    private var faceManager: FaceManager? = null
    init {
        Reflection.unseal(context, listOf("android.hardware.face"))
        faceAuthenticationManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                context.getSystemService(FaceAuthenticationManager::class.java)
            } catch (ignore: Throwable) {
                null
            }
        } else {
            try {
                context.getSystemService("face") as FaceAuthenticationManager
            } catch (ignore: Throwable) {
                null
            }
        }
        faceManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                context.getSystemService(FaceManager::class.java)
            } catch (ignore: Throwable) {
                null
            }
        } else {
            try {
                context.getSystemService("face") as FaceManager
            } catch (ignore: Throwable) {
                null
            }
        }
        listener?.initFinished(biometricMethod, this@AndroidFaceUnlockModule)
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
                } catch (e: Throwable) {
                    e(e, name)
                }


                try {
                    faceManagerIsHardwareDetected = faceManager?.isHardwareDetected == true
                } catch (e: Throwable) {
                    e(e, name)
                }

            return faceManagerIsHardwareDetected || faceAuthenticationManagerIsHardwareDetected
        }

    override fun hasEnrolled(): Boolean {
        var faceAuthenticationManagerHasEnrolled = false
        var faceManagerHasEnrolled = false

            try {
                faceAuthenticationManagerHasEnrolled =
                    faceAuthenticationManager?.javaClass?.getMethod("hasEnrolledFace")
                        ?.invoke(faceAuthenticationManager) as Boolean
            } catch (e: Throwable) {
                e(e, name)
                try {
                    faceAuthenticationManagerHasEnrolled =
                        faceAuthenticationManager?.javaClass?.getMethod("hasEnrolledTemplates")
                            ?.invoke(faceAuthenticationManager) as Boolean
                } catch (e2: Throwable) {
                    e(e2, name)
                }
            }


            try {
                faceManagerHasEnrolled = faceManager?.javaClass?.getMethod("hasEnrolledFace")
                    ?.invoke(faceManager) as Boolean
            } catch (e: Throwable) {
                e(e, name)
                try {
                    faceManagerHasEnrolled = faceManager?.javaClass?.getMethod("hasEnrolledTemplates")
                        ?.invoke(faceManager) as Boolean
                } catch (e2: Throwable) {
                    e(e2, name)
                }
            }

        return faceAuthenticationManagerHasEnrolled || faceManagerHasEnrolled
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
                        ), ExecutorHelper.INSTANCE.handler
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
                        ExecutorHelper.INSTANCE.handler
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
                    BiometricErrorLockoutPermanentFix.INSTANCE.setBiometricSensorPermanentlyLocked(
                        biometricMethod.biometricType
                    )
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                }
                BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS, BiometricCodes.BIOMETRIC_ERROR_NO_SPACE -> failureReason =
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
            listener?.onHelp(getByCode(helpMsgId), helpString)
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
                    BiometricErrorLockoutPermanentFix.INSTANCE.setBiometricSensorPermanentlyLocked(
                        biometricMethod.biometricType
                    )
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                }
                BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS, BiometricCodes.BIOMETRIC_ERROR_NO_SPACE -> failureReason =
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
            listener?.onHelp(getByCode(helpMsgId), helpString)
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