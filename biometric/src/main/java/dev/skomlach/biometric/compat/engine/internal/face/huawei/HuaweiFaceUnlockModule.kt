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

import android.content.Context
import android.os.CancellationSignal
import com.huawei.facerecognition.FaceManager
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationHelpReason
import dev.skomlach.biometric.compat.engine.BiometricCodes
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.engine.internal.face.huawei.impl.HuaweiFaceManager
import dev.skomlach.biometric.compat.engine.internal.face.huawei.impl.HuaweiFaceManagerFactory
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.CodeToString.getErrorCode
import dev.skomlach.biometric.compat.utils.CodeToString.getHelpCode
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper
import java.lang.reflect.InvocationTargetException


class HuaweiFaceUnlockModule(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACE_HUAWEI) {
    //EMUI 10.1.0
    private var huaweiFaceManagerLegacy: HuaweiFaceManager? = null
    private var huawei3DFaceManager: FaceManager? = null

    init {
        ExecutorHelper.post {

            try {
                huawei3DFaceManager = faceManager
                d("$name.huawei3DFaceManager - $huawei3DFaceManager")
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
                huawei3DFaceManager = null
            }
            try {
                huaweiFaceManagerLegacy = HuaweiFaceManagerFactory.getHuaweiFaceManager()
                d("$name.huaweiFaceManagerLegacy - $huaweiFaceManagerLegacy")
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
                huaweiFaceManagerLegacy = null
            }

            listener?.initFinished(biometricMethod, this@HuaweiFaceUnlockModule)
        }
    }

    private val faceManager: FaceManager?
        get() {
            try {
                val t = Class.forName("com.huawei.facerecognition.FaceManagerFactory")
                val method = t.getDeclaredMethod("getFaceManager", Context::class.java)
                return method.invoke(null, context) as FaceManager
            } catch (var3: ClassNotFoundException) {
                d("$name.Throw exception: ClassNotFoundException")
            } catch (var4: NoSuchMethodException) {
                d("$name.Throw exception: NoSuchMethodException")
            } catch (var5: IllegalAccessException) {
                d("$name.Throw exception: IllegalAccessException")
            } catch (var6: InvocationTargetException) {
                d("$name.Throw exception: InvocationTargetException")
            }
            return null
        }

    override fun getManagers(): Set<Any> {
        val managers = HashSet<Any>()
        //pass only EMUI 10.1.0 manager
        huawei3DFaceManager?.let {
            managers.add(it)
        }
        return managers
    }

    override fun getIds(manager: Any): List<String> {
        val ids = ArrayList<String>(super.getIds(manager))
        huaweiFaceManagerLegacy?.let {
            it.getEnrolledTemplates()?.let { array ->
                for (a in array)
                    ids.add("$a")
            }
        }
        return ids
    }

    override val isManagerAccessible: Boolean
        get() = huaweiFaceManagerLegacy != null || huawei3DFaceManager != null
    override val isHardwarePresent: Boolean
        get() {
            try {
                if (huawei3DFaceManager?.isHardwareDetected == true) return true
            } catch (e: Throwable) {
                e(e, name)
            }
            try {
                if (huaweiFaceManagerLegacy?.isHardwareDetected == true) return true
            } catch (e: Throwable) {
                e(e, name)
            }

            return false
        }

    override fun hasEnrolled(): Boolean {
        try {
            val hasEnrolled = try {
                huawei3DFaceManager?.hasEnrolledTemplates() == true
            } catch (ignore: Throwable) {
                val m = huawei3DFaceManager?.javaClass?.declaredMethods?.firstOrNull {
                    it.name.contains("hasEnrolled", ignoreCase = true)
                }
                val isAccessible = m?.isAccessible ?: true
                var result = false
                try {
                    if (!isAccessible)
                        m?.isAccessible = true
                    if (m?.returnType == Boolean::class.javaPrimitiveType)
                        result = (m?.invoke(huawei3DFaceManager) as Boolean?) == true
                    else
                        if (m?.returnType == Int::class.javaPrimitiveType)
                            result = (m?.invoke(huawei3DFaceManager) as Int?) ?: 0 > 0
                } finally {
                    if (!isAccessible)
                        m?.isAccessible = false
                }
                result
            }
            if (huawei3DFaceManager?.isHardwareDetected == true && hasEnrolled) return true
        } catch (e: Throwable) {
            e(e, name)
        }
        try {
            if (huaweiFaceManagerLegacy?.isHardwareDetected == true && huaweiFaceManagerLegacy?.hasEnrolledTemplates() == true) return true
        } catch (e: Throwable) {
            e(e, name)
        }

        return false
    }

    @Throws(SecurityException::class)
    override fun authenticate(
        cancellationSignal: androidx.core.os.CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        try {
            d("$name.authenticate - $biometricMethod")
            if (!isHardwarePresent) {
                listener?.onFailure(AuthenticationFailureReason.NO_HARDWARE, tag())
                return
            }
            if (!hasEnrolled()) {
                listener?.onFailure(AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED, tag())
                return
            }
            // Why getCancellationSignalObject returns an Object is unexplained
            val signalObject =
                (if (cancellationSignal == null) null else cancellationSignal.cancellationSignalObject as android.os.CancellationSignal?)
                    ?: throw IllegalArgumentException("CancellationSignal cann't be null")

            if (huawei3DFaceManager?.isHardwareDetected == true && huawei3DFaceManager?.hasEnrolledTemplates() == true) {
                huawei3DFaceManager?.let {
                    try {
                        // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                        it.authenticate(
                            null,
                            signalObject,
                            0,
                            AuthCallback3DFace(restartPredicate, cancellationSignal, listener),
                            ExecutorHelper.handler
                        )
                        return
                    } catch (e: Throwable) {
                        e(e, "$name: authenticate failed unexpectedly")
                    }
                }
            }
            if (huaweiFaceManagerLegacy?.isHardwareDetected == true && huaweiFaceManagerLegacy?.hasEnrolledTemplates() == true) {
                huaweiFaceManagerLegacy?.let {
                    try {
                        signalObject.setOnCancelListener(CancellationSignal.OnCancelListener {
                            it.cancel(
                                0
                            )
                        })
                        // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                        it.authenticate(
                            0,
                            0,
                            AuthCallbackLegacy(restartPredicate, cancellationSignal, listener)
                        )
                        return
                    } catch (e: Throwable) {
                        e(e, "$name: authenticate failed unexpectedly")
                    }
                }
            }
        } catch (e: Throwable) {
            e(e, "$name: authenticate failed unexpectedly")
        }
        listener?.onFailure(AuthenticationFailureReason.UNKNOWN, tag())
    }

    private inner class AuthCallback3DFace(
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: androidx.core.os.CancellationSignal?,
        private val listener: AuthenticationListener?
    ) : FaceManager.AuthenticationCallback() {
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence) {
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
                else -> {
                    Core.cancelAuthentication(this@HuaweiFaceUnlockModule)
                    listener?.onCanceled(tag())
                    return
                }
            }
            if (restartCauseTimeout(failureReason)) {
                authenticate(cancellationSignal, listener, restartPredicate)
            } else
                if (restartPredicate?.invoke(failureReason) == true) {
                    listener?.onFailure(failureReason, tag())
                    authenticate(cancellationSignal, listener, restartPredicate)
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
            d(name + ".onAuthenticationHelp: " + getHelpCode(helpMsgId) + "-" + helpString)
            listener?.onHelp(AuthenticationHelpReason.getByCode(helpMsgId), helpString)
        }

        override fun onAuthenticationSucceeded(result: FaceManager.AuthenticationResult) {
            d("$name.onAuthenticationSucceeded: $result")
            listener?.onSuccess(tag())
        }

        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            listener?.onFailure(AuthenticationFailureReason.AUTHENTICATION_FAILED, tag())
        }
    }

    private inner class AuthCallbackLegacy(
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: androidx.core.os.CancellationSignal?,
        private val listener: AuthenticationListener?
    ) : HuaweiFaceManager.AuthenticatorCallback() {
        override fun onAuthenticationError(errMsgId: Int) {
            d(name + ".onAuthenticationError: " + getErrorCode(errMsgId))
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
                else -> {
                    Core.cancelAuthentication(this@HuaweiFaceUnlockModule)
                    listener?.onCanceled(tag())
                    return
                }
            }
            if (restartCauseTimeout(failureReason)) {
                authenticate(cancellationSignal, listener, restartPredicate)
            } else
                if (restartPredicate?.invoke(failureReason) == true) {
                    listener?.onFailure(failureReason, tag())
                    huaweiFaceManagerLegacy?.cancel(0)
                    ExecutorHelper.postDelayed({
                        authenticate(cancellationSignal, listener, restartPredicate)
                    }, 250)
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

        override fun onAuthenticationStatus(helpMsgId: Int) {
            d(name + ".onAuthenticationHelp: " + getHelpCode(helpMsgId))
            listener?.onHelp(AuthenticationHelpReason.getByCode(helpMsgId), null)
        }

        override fun onAuthenticationSucceeded() {
            d("$name.onAuthenticationSucceeded: ")
            listener?.onSuccess(tag())
        }

        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            onAuthenticationError(BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS)
        }
    }
}