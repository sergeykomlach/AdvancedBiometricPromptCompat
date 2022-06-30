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

package dev.skomlach.biometric.compat.engine.internal.iris.samsung

import android.annotation.SuppressLint
import androidx.core.os.CancellationSignal
import com.samsung.android.camera.iris.SemIrisManager
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


class SamsungIrisUnlockModule @SuppressLint("WrongConstant") constructor(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.IRIS_SAMSUNG) {
    companion object {
        const val IRIS_ACQUIRED_CAPTURE_COMPLETED = 10003
        const val IRIS_ACQUIRED_CAPTURE_FAILED = 10006
        const val IRIS_ACQUIRED_CAPTURE_IRIS_LEAVE = 10004
        const val IRIS_ACQUIRED_CAPTURE_IRIS_LEAVE_TIMEOUT = 10007
        const val IRIS_ACQUIRED_CAPTURE_READY = 10001
        const val IRIS_ACQUIRED_CAPTURE_STARTED = 10002
        const val IRIS_ACQUIRED_CAPTURE_SUCCESS = 10005
        const val IRIS_ACQUIRED_CHANGE_YOUR_POSITION = 12
        const val IRIS_ACQUIRED_DUPLICATED_SCANNED_IMAGE = 1002
        const val IRIS_ACQUIRED_EYE_NOT_PRESENT = 10
        const val IRIS_ACQUIRED_FACTORY_TEST_SNSR_TEST_SCRIPT_END = 10009
        const val IRIS_ACQUIRED_FACTORY_TEST_SNSR_TEST_SCRIPT_START = 10008
        const val IRIS_ACQUIRED_GOOD = 0
        const val IRIS_ACQUIRED_INSUFFICIENT = 2
        const val IRIS_ACQUIRED_MOVE_CLOSER = 3
        const val IRIS_ACQUIRED_MOVE_DOWN = 8
        const val IRIS_ACQUIRED_MOVE_FARTHER = 4
        const val IRIS_ACQUIRED_MOVE_LEFT = 5
        const val IRIS_ACQUIRED_MOVE_RIGHT = 6
        const val IRIS_ACQUIRED_MOVE_SOMEWHERE_DARKER = 11
        const val IRIS_ACQUIRED_MOVE_UP = 7
        const val IRIS_ACQUIRED_OPEN_EYES_WIDER = 9
        const val IRIS_ACQUIRED_PARTIAL = 1
        const val IRIS_ACQUIRED_VENDOR_BASE = 1000
        const val IRIS_ACQUIRED_VENDOR_EVENT_BASE = 10000
        const val IRIS_AUTH_TYPE_NONE = 0
        const val IRIS_AUTH_TYPE_PREVIEW_CALLBACK = 1
        const val IRIS_AUTH_TYPE_UI_NO_PREVIEW = 3
        const val IRIS_AUTH_TYPE_UI_WITH_PREVIEW = 2
        const val IRIS_DISABLE_PREVIEW_CALLBACK = 7
        const val IRIS_ENABLE_PREVIEW_CALLBACK = 6
        const val IRIS_ERROR_AUTH_VIEW_SIZE = 10
        const val IRIS_ERROR_AUTH_WINDOW_TOKEN = 11
        const val IRIS_ERROR_CANCELED = 4
        const val IRIS_ERROR_DEVICE_NEED_RECAL = 1001
        const val IRIS_ERROR_EVICTED = 13
        const val IRIS_ERROR_EVICTED_DUE_TO_VIDEO_CALL = 14
        const val IRIS_ERROR_EYE_SAFETY_TIMEOUT = 9
        const val IRIS_ERROR_HW_UNAVAILABLE = 0
        const val IRIS_ERROR_IDENTIFY_FAILURE_BROKEN_DATABASE = 1004
        const val IRIS_ERROR_IDENTIFY_FAILURE_SENSOR_CHANGED = 1005
        const val IRIS_ERROR_IDENTIFY_FAILURE_SERVICE_FAILURE = 1003
        const val IRIS_ERROR_IDENTIFY_FAILURE_SYSTEM_FAILURE = 1002
        const val IRIS_ERROR_LOCKOUT = 6
        const val IRIS_ERROR_NEED_TO_RETRY = 5000
        const val IRIS_ERROR_NO_EYE_DETECTED = 15
        const val IRIS_ERROR_NO_SPACE = 3
        const val IRIS_ERROR_OPEN_IR_CAMERA_FAIL = 8
        const val IRIS_ERROR_PROXIMITY_TIMEOUT = 12
        const val IRIS_ERROR_START_IR_CAMERA_PREVIEW_FAIL = 7
        const val IRIS_ERROR_TIMEOUT = 2
        const val IRIS_ERROR_UNABLE_TO_PROCESS = 1
        const val IRIS_ERROR_UNABLE_TO_REMOVE = 5
        const val IRIS_ERROR_VENDOR_BASE = 1000
        const val IRIS_INVISIBLE_PREVIEW = 4
        const val IRIS_ONE_EYE = 40000
        const val IRIS_REQUEST_DVFS_FREQUENCY = 1004
        const val IRIS_REQUEST_ENROLL_SESSION = 1002
        const val IRIS_REQUEST_ENUMERATE = 11
        const val IRIS_REQUEST_FACTORY_TEST_ALWAYS_LED_ON = 2001
        const val IRIS_REQUEST_FACTORY_TEST_CAMERA_VERSION = 2004
        const val IRIS_REQUEST_FACTORY_TEST_CAPTURE = 2002
        const val IRIS_REQUEST_FACTORY_TEST_FULL_PREVIEW = 2000
        const val IRIS_REQUEST_FACTORY_TEST_PREVIEW_MODE = 2003
        const val IRIS_REQUEST_GET_IR_IDS = 1003
        const val IRIS_REQUEST_GET_SENSOR_INFO = 5
        const val IRIS_REQUEST_GET_SENSOR_STATUS = 6
        const val IRIS_REQUEST_GET_UNIQUE_ID = 7
        const val IRIS_REQUEST_GET_USERIDS = 12
        const val IRIS_REQUEST_GET_VERSION = 4
        const val IRIS_REQUEST_IR_PREVIEW_ENABLE = 2005
        const val IRIS_REQUEST_LOCKOUT = 1001
        const val IRIS_REQUEST_PAUSE = 0
        const val IRIS_REQUEST_PROCESS_FIDO = 9
        const val IRIS_REQUEST_REMOVE_IRIS = 1000
        const val IRIS_REQUEST_RESUME = 1
        const val IRIS_REQUEST_SENSOR_TEST_NORMALSCAN = 3
        const val IRIS_REQUEST_SESSION_OPEN = 2
        const val IRIS_REQUEST_SET_ACTIVE_GROUP = 8
        const val IRIS_REQUEST_TZ_STATUS = 13
        const val IRIS_REQUEST_UPDATE_SID = 10
        const val IRIS_TWO_EYES = 40001
        const val IRIS_VISIBLE_PREVIEW = 5
    }

    private var manager: SemIrisManager? = null

    init {

        manager = try {
            SemIrisManager.getSemIrisManager(context)
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)
            null
        }


        listener?.initFinished(biometricMethod, this@SamsungIrisUnlockModule)
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
                e(e, name)
            }

            return false
        }

    override fun hasEnrolled(): Boolean {
        try {
            return manager?.isHardwareDetected == true && manager?.hasEnrolledIrises() == true
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
        d("$name.authenticate - $biometricMethod")
        manager?.let {
            try {
                val callback: SemIrisManager.AuthenticationCallback =
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

                // Occasionally, an NPE will bubble up out of SemIrisManager.authenticate
                val crypto = if (biometricCryptoObject == null) null else {

                    if (biometricCryptoObject.cipher != null)
                        SemIrisManager.CryptoObject(biometricCryptoObject.cipher, null)
                    else if (biometricCryptoObject.mac != null)
                        SemIrisManager.CryptoObject(biometricCryptoObject.mac, null)
                    if (biometricCryptoObject.signature != null)
                        SemIrisManager.CryptoObject(biometricCryptoObject.signature, null)
                    else
                        null
                }
                it.authenticate(
                    crypto,
                    signalObject,
                    0,
                    callback,
                    ExecutorHelper.handler,
                    null
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
    ) : SemIrisManager.AuthenticationCallback() {
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
            d("$name.onAuthenticationError: $errMsgId-$errString")
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (errMsgId) {
//                IRIS_ERROR_NO_EYE_DETECTED -> failureReason =
//                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED // AUTHENTICATION_FAILED
                IRIS_ERROR_HW_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE

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
                    Core.cancelAuthentication(this@SamsungIrisUnlockModule)
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
                }
        }

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
            d("$name.onAuthenticationHelp: $helpMsgId-$helpString")
            listener?.onHelp(helpString)
        }

        override fun onAuthenticationSucceeded(result: SemIrisManager.AuthenticationResult?) {
            d("$name.onAuthenticationSucceeded: $result")
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
            var failureReason = AuthenticationFailureReason.AUTHENTICATION_FAILED
            if (restartPredicate?.invoke(failureReason) == true) {
                listener?.onFailure(failureReason, tag())
                authenticate(biometricCryptoObject, cancellationSignal, listener, restartPredicate)
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