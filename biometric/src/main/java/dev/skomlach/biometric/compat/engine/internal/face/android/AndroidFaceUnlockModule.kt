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

package dev.skomlach.biometric.compat.engine.internal.face.android

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.hardware.biometrics.CryptoObject
import android.hardware.face.FaceManager
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


class AndroidFaceUnlockModule @SuppressLint("WrongConstant") constructor(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACE_ANDROIDAPI) {
    companion object {

        /**
         * The hardware is unavailable. Try again later.
         */
        const val FACE_ERROR_HW_UNAVAILABLE = 1

        /**
         * Error state returned when the sensor was unable to process the current image.
         */
        const val FACE_ERROR_UNABLE_TO_PROCESS = 2

        /**
         * Error state returned when the current request has been running too long. This is intended to
         * prevent programs from waiting for the face authentication sensor indefinitely. The timeout is
         * platform and sensor-specific, but is generally on the order of 30 seconds.
         */
        const val FACE_ERROR_TIMEOUT = 3

        /**
         * Error state returned for operations like enrollment; the operation cannot be completed
         * because there's not enough storage remaining to complete the operation.
         */
        const val FACE_ERROR_NO_SPACE = 4

        /**
         * The operation was canceled because the face authentication sensor is unavailable. For
         * example, this may happen when the user is switched, the device is locked or another pending
         * operation prevents or disables it.
         */
        const val FACE_ERROR_CANCELED = 5

        /**
         * The [FaceManager.remove] call failed. Typically this will happen when the
         * provided face id was incorrect.
         */
        const val FACE_ERROR_UNABLE_TO_REMOVE = 6

        /**
         * The operation was canceled because the API is locked out due to too many attempts.
         * This occurs after 5 failed attempts, and lasts for 30 seconds.
         */
        const val FACE_ERROR_LOCKOUT = 7

        /**
         * Hardware vendors may extend this list if there are conditions that do not fall under one of
         * the above categories. Vendors are responsible for providing error strings for these errors.
         * These messages are typically reserved for internal operations such as enrollment, but may be
         * used to express vendor errors not covered by the ones in HAL h file. Applications are
         * expected to show the error message string if they happen, but are advised not to rely on the
         * message id since they will be device and vendor-specific
         */
        const val FACE_ERROR_VENDOR = 8

        /**
         * The operation was canceled because FACE_ERROR_LOCKOUT occurred too many times.
         * Face authentication is disabled until the user unlocks with strong authentication
         * (PIN/Pattern/Password)
         */
        const val FACE_ERROR_LOCKOUT_PERMANENT = 9

        /**
         * The user canceled the operation. Upon receiving this, applications should use alternate
         * authentication (e.g. a password). The application should also provide the means to return
         * to face authentication, such as a "use face authentication" button.
         */
        const val FACE_ERROR_USER_CANCELED = 10

        /**
         * The user does not have a face enrolled.
         */
        const val FACE_ERROR_NOT_ENROLLED = 11

        /**
         * The device does not have a face sensor. This message will propagate if the calling app
         * ignores the result from PackageManager.hasFeature(FEATURE_FACE) and calls
         * this API anyway. Apps should always check for the feature before calling this API.
         */
        const val FACE_ERROR_HW_NOT_PRESENT = 12

        /**
         * The user pressed the negative button. This is a placeholder that is currently only used
         * by the support library.
         */
        const val FACE_ERROR_NEGATIVE_BUTTON = 13

        /**
         * The device does not have pin, pattern, or password set up. See
         * [BiometricPrompt.Builder.setDeviceCredentialAllowed] and
         * [KeyguardManager.isDeviceSecure]
         */
        var FACE_ERROR_NO_DEVICE_CREDENTIAL = 14

        /**
         * A security vulnerability has been discovered and the sensor is unavailable until a
         * security update has addressed this issue. This error can be received if for example,
         * authentication was requested with [Authenticators.FACE_STRONG], but the
         * sensor's strength can currently only meet [Authenticators.FACE_WEAK].
         */
        var FACE_ERROR_SECURITY_UPDATE_REQUIRED = 15

        var FACE_ERROR_NO_FACE_DETECTED = 1006

        /**
         * Authentication cannot proceed because re-enrollment is required.
         */
        var FACE_ERROR_RE_ENROLL = 16

        /**
         * Unknown error received from the HAL.
         */
        const val FACE_ERROR_UNKNOWN = 17

        /**
         * Vendor codes received from the HAL start at 0. Codes that the framework exposes to keyguard
         * append this value for some reason. We should probably remove this and just send the actual
         * vendor code.
         */
        const val FACE_ERROR_VENDOR_BASE = 1000

        //
        // Image acquisition messages. These will not be sent to the user, since they conflict with
        // existing constants. These must agree with face@1.0/types.hal.
        //
        /**
         * The image acquired was good.
         */
        const val FACE_ACQUIRED_GOOD = 0

        /**
         * The face image was not good enough to process due to a detected condition.
         * (See [or @link #FACE_ACQUIRED_TOO_DARK][.FACE_ACQUIRED_TOO_BRIGHT]).
         */
        const val FACE_ACQUIRED_INSUFFICIENT = 1

        /**
         * The face image was too bright due to too much ambient light.
         * For example, it's reasonable to return this after multiple
         * [.FACE_ACQUIRED_INSUFFICIENT]
         * The user is expected to take action to retry in better lighting conditions
         * when this is returned.
         */
        const val FACE_ACQUIRED_TOO_BRIGHT = 2

        /**
         * The face image was too dark due to illumination light obscured.
         * For example, it's reasonable to return this after multiple
         * [.FACE_ACQUIRED_INSUFFICIENT]
         * The user is expected to take action to retry in better lighting conditions
         * when this is returned.
         */
        const val FACE_ACQUIRED_TOO_DARK = 3

        /**
         * The detected face is too close to the sensor, and the image can't be processed.
         * The user should be informed to move farther from the sensor when this is returned.
         */
        const val FACE_ACQUIRED_TOO_CLOSE = 4

        /**
         * The detected face is too small, as the user might be too far from the sensor.
         * The user should be informed to move closer to the sensor when this is returned.
         */
        const val FACE_ACQUIRED_TOO_FAR = 5

        /**
         * Only the upper part of the face was detected. The sensor field of view is too high.
         * The user should be informed to move up with respect to the sensor when this is returned.
         */
        const val FACE_ACQUIRED_TOO_HIGH = 6

        /**
         * Only the lower part of the face was detected. The sensor field of view is too low.
         * The user should be informed to move down with respect to the sensor when this is returned.
         */
        const val FACE_ACQUIRED_TOO_LOW = 7

        /**
         * Only the right part of the face was detected. The sensor field of view is too far right.
         * The user should be informed to move to the right with respect to the sensor
         * when this is returned.
         */
        const val FACE_ACQUIRED_TOO_RIGHT = 8

        /**
         * Only the left part of the face was detected. The sensor field of view is too far left.
         * The user should be informed to move to the left with respect to the sensor
         * when this is returned.
         */
        const val FACE_ACQUIRED_TOO_LEFT = 9

        /**
         * The user's eyes have strayed away from the sensor. If this message is sent, the user should
         * be informed to look at the device. If the user can't be found in the frame, one of the other
         * acquisition messages should be sent, e.g. FACE_ACQUIRED_NOT_DETECTED.
         */
        const val FACE_ACQUIRED_POOR_GAZE = 10

        /**
         * No face was detected in front of the sensor.
         * The user should be informed to point the sensor to a face when this is returned.
         */
        const val FACE_ACQUIRED_NOT_DETECTED = 11

        /**
         * Too much motion was detected.
         * The user should be informed to keep their face steady relative to the
         * sensor.
         */
        const val FACE_ACQUIRED_TOO_MUCH_MOTION = 12

        /**
         * The sensor needs to be re-calibrated. This is an unexpected condition, and should only be
         * sent if a serious, uncorrectable, and unrecoverable calibration issue is detected which
         * requires user intervention, e.g. re-enrolling. The expected response to this message is to
         * direct the user to re-enroll.
         */
        const val FACE_ACQUIRED_RECALIBRATE = 13

        /**
         * The face is too different from a previous acquisition. This condition
         * only applies to enrollment. This can happen if the user passes the
         * device to someone else in the middle of enrollment.
         */
        const val FACE_ACQUIRED_TOO_DIFFERENT = 14

        /**
         * The face is too similar to a previous acquisition. This condition only
         * applies to enrollment. The user should change their pose.
         */
        const val FACE_ACQUIRED_TOO_SIMILAR = 15

        /**
         * The magnitude of the pan angle of the user’s face with respect to the sensor’s
         * capture plane is too high.
         *
         * The pan angle is defined as the angle swept out by the user’s face turning
         * their neck left and right. The pan angle would be zero if the user faced the
         * camera directly.
         *
         * The user should be informed to look more directly at the camera.
         */
        const val FACE_ACQUIRED_PAN_TOO_EXTREME = 16

        /**
         * The magnitude of the tilt angle of the user’s face with respect to the sensor’s
         * capture plane is too high.
         *
         * The tilt angle is defined as the angle swept out by the user’s face looking up
         * and down. The tilt angle would be zero if the user faced the camera directly.
         *
         * The user should be informed to look more directly at the camera.
         */
        const val FACE_ACQUIRED_TILT_TOO_EXTREME = 17

        /**
         * The magnitude of the roll angle of the user’s face with respect to the sensor’s
         * capture plane is too high.
         *
         * The roll angle is defined as the angle swept out by the user’s face tilting their head
         * towards their shoulders to the left and right. The roll angle would be zero if the user's
         * head is vertically aligned with the camera.
         *
         * The user should be informed to look more directly at the camera.
         */
        const val FACE_ACQUIRED_ROLL_TOO_EXTREME = 18

        /**
         * The user’s face has been obscured by some object.
         *
         * The user should be informed to remove any objects from the line of sight from
         * the sensor to the user’s face.
         */
        const val FACE_ACQUIRED_FACE_OBSCURED = 19

        /**
         * This message represents the earliest message sent at the beginning of the authentication
         * pipeline. It is expected to be used to measure latency. For example, in a camera-based
         * authentication system it's expected to be sent prior to camera initialization. Note this
         * should be sent whenever authentication is restarted (see IBiometricsFace#userActivity).
         * The framework will measure latency based on the time between the last START message and the
         * onAuthenticated callback.
         */
        const val FACE_ACQUIRED_START = 20

        /**
         * The sensor is dirty. The user should be informed to clean the sensor.
         */
        const val FACE_ACQUIRED_SENSOR_DIRTY = 21

        /**
         * Hardware vendors may extend this list if there are conditions that do not fall under one of
         * the above categories. Vendors are responsible for providing error strings for these errors.
         */
        const val FACE_ACQUIRED_VENDOR = 22

        /**
         * Unknown acquired code received from the HAL.
         */
        const val FACE_ACQUIRED_UNKNOWN = 23

        /**
         * The first frame from the camera has been received.
         */
        const val FACE_ACQUIRED_FIRST_FRAME_RECEIVED = 24

        /**
         * Dark glasses detected. This can be useful for providing relevant feedback to the user and
         * enabling an alternative authentication logic if the implementation supports it.
         */
        const val FACE_ACQUIRED_DARK_GLASSES_DETECTED = 25

        /**
         * A face mask or face covering detected. This can be useful for providing relevant feedback to
         * the user and enabling an alternative authentication logic if the implementation supports it.
         */
        const val FACE_ACQUIRED_MOUTH_COVERING_DETECTED = 26

        /**
         * Vendor codes received from the HAL start at 0. Codes that the framework exposes to keyguard
         * append this value for some reason. We should probably remove this and just send the actual
         * vendor code.
         */
        const val FACE_ACQUIRED_VENDOR_BASE = 1000
    }

    private var manager: FaceManager? = null

    init {

        try {
            manager = ContextCompat.getSystemService(context, FaceManager::class.java)
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)
        }

        if (manager == null) {
            try {
                manager = context.getSystemService("face") as FaceManager?
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
            }
        }
        listener?.initFinished(biometricMethod, this@AndroidFaceUnlockModule)
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
                return manager?.hasEnrolledTemplates() == true
            } catch (e: Throwable) {

            }
            try {
                return manager?.getEnrolledFaces()?.isNotEmpty() == true
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
                val callback =
                    FaceManagerAuthCallback(
                        biometricCryptoObject,
                        restartPredicate,
                        cancellationSignal,
                        listener
                    )
                // Occasionally, an NPE will bubble up out of SemBioSomeManager.authenticate
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
                } catch (e: Throwable) {
                }
                try {
                    authCallTimestamp.set(System.currentTimeMillis())
                    it.authenticate(
                        crypto,
                        signalObject,
                        callback,
                        ExecutorHelper.handler,
                        getUserId(),
                        true
                    )
                    return
                } catch (e: Throwable) {
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
                } catch (e: Throwable) {
                }
                authCallTimestamp.set(System.currentTimeMillis())
                it.authenticate(
                    crypto,
                    signalObject,
                    0,
                    callback,
                    ExecutorHelper.handler,
                    getUserId()
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

    internal inner class FaceManagerAuthCallback(
        private val biometricCryptoObject: BiometricCryptoObject?,
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: CancellationSignal?,
        private val listener: AuthenticationListener?
    ) : FaceManager.AuthenticationCallback() {
        private var errorTs = 0L
        private val skipTimeout =
            context.resources.getInteger(android.R.integer.config_shortAnimTime)
        private var selfCanceled = false
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
            d("$name.onAuthenticationError: $errMsgId-$errString")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout)
                return
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (errMsgId) {
                FACE_ERROR_NO_FACE_DETECTED -> failureReason =
                    AuthenticationFailureReason.AUTHENTICATION_FAILED

                FACE_ERROR_NOT_ENROLLED -> failureReason =
                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED

                FACE_ERROR_HW_NOT_PRESENT, FACE_ERROR_SECURITY_UPDATE_REQUIRED -> failureReason =
                    AuthenticationFailureReason.NO_HARDWARE

                FACE_ERROR_HW_UNAVAILABLE, FACE_ERROR_NO_DEVICE_CREDENTIAL, FACE_ERROR_RE_ENROLL -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE

                FACE_ERROR_LOCKOUT_PERMANENT -> {
                    BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(
                        biometricMethod.biometricType
                    )
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                }

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

                FACE_ERROR_CANCELED, FACE_ERROR_NEGATIVE_BUTTON, FACE_ERROR_USER_CANCELED -> {
                    if (!selfCanceled) {
                        listener?.onCanceled(tag())
                        Core.cancelAuthentication(this@AndroidFaceUnlockModule)
                    }
                    return
                }

                else -> {
                    if (!selfCanceled) {
                        listener?.onFailure(failureReason, tag())
                        postCancelTask {
                            if (cancellationSignal?.isCanceled == false) {
                                selfCanceled = true
                                listener?.onCanceled(tag())
                                Core.cancelAuthentication(this@AndroidFaceUnlockModule)
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
                    postCancelTask {
                        if (cancellationSignal?.isCanceled == false) {
                            selfCanceled = true
                            listener?.onCanceled(tag())
                            Core.cancelAuthentication(this@AndroidFaceUnlockModule)
                        }
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
                    result?.cryptoObject?.getSignature(),
                    result?.cryptoObject?.getCipher(),
                    result?.cryptoObject?.getMac()
                )
            )
        }

        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            listener?.onFailure(AuthenticationFailureReason.AUTHENTICATION_FAILED, tag())
        }
    }

}