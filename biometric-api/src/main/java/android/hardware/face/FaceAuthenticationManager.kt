package android.hardware.face

import android.os.CancellationSignal
import android.os.Handler
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.Mac

/**
 * Copyright (C) 2014 The Android Open Source Project
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * A class that coordinates access to the face authentication hardware.
 */
class FaceAuthenticationManager {
    /**
     * Request authentication of a crypto object. This call operates the face recognition sensor
     * and starts capturing images. It terminates when
     * [AuthenticationCallback.onAuthenticationError] or
     * [AuthenticationCallback.onAuthenticationSucceeded] is called, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     *
     * @param crypto   object associated with the call or null if none required.
     * @param cancel   an object that can be used to cancel authentication
     * @param flags    optional flags; should be 0
     * @param callback an object to receive authentication events
     * @param handler  an optional handler to handle callback events
     * @throws IllegalArgumentException if the crypto operation is not supported or is not backed
     * by [Android Keystore
 * facility]({@docRoot}training/articles/keystore.html).
     * @throws IllegalStateException    if the crypto primitive is not initialized.
     */
    fun authenticate(
        crypto: CryptoObject?, cancel: CancellationSignal?,
        flags: Int, callback: AuthenticationCallback, handler: Handler?
    ) {
    }

    /**
     * Per-user version
     *
     * @hide
     */
    fun authenticate(
        crypto: CryptoObject?, cancel: CancellationSignal?,
        flags: Int, callback: AuthenticationCallback, handler: Handler?, userId: Int
    ) {
    }

    /**
     * Request face authentication enrollment. This call operates the face recognition sensor
     * and starts capturing images. Progress will be indicated by callbacks to the
     * [EnrollmentCallback] object. It terminates when
     * [EnrollmentCallback.onEnrollmentError] or
     * [is called with remaining == 0, at][EnrollmentCallback.onEnrollmentProgress]
     */
    fun enroll(
        token: ByteArray?, cancel: CancellationSignal?, flags: Int,
        userId: Int, callback: EnrollmentCallback?
    ) {
    }

    /**
     * Requests a pre-enrollment auth token to tie enrollment to the confirmation of
     * existing device credentials (e.g. pin/pattern/password).
     *
     * @hide
     */
    fun preEnroll(): Long {
        return 0
    }

    /**
     * Finishes enrollment and cancels the current auth token.
     *
     * @hide
     */
    fun postEnroll(): Int {
        return 0
    }

    /**
     * Sets the active user. This is meant to be used to select the current profile for enrollment
     * to allow separate enrolled faces for a work profile
     *
     * @param userId
     * @hide
     */
    fun setActiveUser(userId: Int) {}

    /**
     * Remove given face template from face hardware and/or protected storage.
     *
     * @param face     the face item to remove
     * @param userId   the user who this face belongs to
     * @param callback an optional callback to verify that face templates have been
     * successfully removed. May be null if no callback is required.
     * @hide
     */
    fun remove(face: Face?, userId: Int, callback: RemovalCallback?) {}

    /**
     * Obtain the enrolled face template.
     *
     * @return the current face item
     * @hide
     */
    fun getEnrolledFace(userId: Int): Face? {
        return null
    }

    /**
     * Obtain the enrolled face template.
     *
     * @return the current face item
     * @hide
     */
    val enrolledFace: Face?
        get() = null

    /**
     * Determine if there is a face enrolled.
     *
     * @return true if a face is enrolled, false otherwise
     */
    fun hasEnrolledFace(): Boolean {
        return false
    }

    /**
     * @hide
     */
    fun hasEnrolledFace(userId: Int): Boolean {
        return false
    }

    /**
     * Determine if face authentication sensor hardware is present and functional.
     *
     * @return true if hardware is present and functional, false otherwise.
     */
    val isHardwareDetected: Boolean
        get() = false

    /**
     * Retrieves the authenticator token for binding keys to the lifecycle
     * of the calling user's face. Used only by internal clients.
     *
     * @hide
     */
    val authenticatorId: Long
        get() = 0

    class CryptoObject {
        val signature: Signature?
            get() = null
        val cipher: Cipher?
            get() = null
        val mac: Mac?
            get() = null
    }

    /**
     * Container for callback data from [FaceAuthenticationManager.authenticate].
     */
    class AuthenticationResult
    /**
     * Authentication result
     *
     * @param crypto the crypto object
     * @param face   the recognized face data, if allowed.
     * @hide
     */
        (crypto: CryptoObject?, face: Face?, userId: Int) {
        /**
         * Obtain the crypto object associated with this transaction
         *
         * @return crypto object provided to [FaceAuthenticationManager.authenticate].
         */
        val cryptoObject: CryptoObject?
            get() = null

        /**
         * Obtain the Face associated with this operation. Applications are strongly
         * discouraged from associating specific faces with specific applications or operations.
         *
         * @hide
         */
        val face: Face?
            get() = null

        /**
         * Obtain the userId for which this face was authenticated.
         *
         * @hide
         */
        val userId: Int
            get() = 0
    }

    /**
     * Callback structure provided to [FaceAuthenticationManager.authenticate]. Users of [ ][FaceAuthenticationManager.authenticate] must provide an implementation of this for listening
     * to face events.
     */
    abstract class AuthenticationCallback {
        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further callbacks will be made on this object.
         *
         * @param errorCode An integer identifying the error message
         * @param errString A human-readable error string that can be shown in UI
         */
        open fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {}

        /**
         * Called when a recoverable error has been encountered during authentication. The help
         * string is provided to give the user guidance for what went wrong, such as
         * "Sensor dirty, please clean it."
         *
         * @param helpCode   An integer identifying the error message
         * @param helpString A human-readable string that can be shown in UI
         */
        open fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {}

        /**
         * Called when a face is recognized.
         *
         * @param result An object containing authentication-related data
         */
        open fun onAuthenticationSucceeded(result: AuthenticationResult?) {}

        /**
         * Called when a face is detected but not recognized.
         */
        open fun onAuthenticationFailed() {}

        /**
         * Called when a face image has been acquired, but wasn't processed yet.
         *
         * @param acquireInfo one of FACE_ACQUIRED_* constants
         * @hide
         */
        fun onAuthenticationAcquired(acquireInfo: Int) {}
    }

    /**
     * Callback structure provided to
     * must provide an implementation of this to  for listening to face enrollment events.
     *
     * @hide
     */
    abstract class EnrollmentCallback {
        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further callbacks will be made on this object.
         *
         * @param errMsgId  An integer identifying the error message
         * @param errString A human-readable error string that can be shown in UI
         */
        fun onEnrollmentError(errMsgId: Int, errString: CharSequence?) {}

        /**
         * Called when a recoverable error has been encountered during enrollment. The help
         * string is provided to give the user guidance for what went wrong, such as
         * "Sensor dirty, please clean it" or what they need to do next, such as
         * "Rotate face up / down."
         *
         * @param helpMsgId  An integer identifying the error message
         * @param helpString A human-readable string that can be shown in UI
         */
        fun onEnrollmentHelp(helpMsgId: Int, helpString: CharSequence?) {}

        /**
         * Called as each enrollment step progresses. Enrollment is considered complete when
         * remaining reaches 0. This function will not be called if enrollment fails. See
         * [EnrollmentCallback.onEnrollmentError]
         *
         * @param remaining The number of remaining steps
         */
        fun onEnrollmentProgress(remaining: Int) {}
    }

    /**
     * Callback structure provided to [.remove]. Users of [FaceAuthenticationManager] may
     * optionally provide an implementation of this to
     * [.remove] for listening to face template
     * removal events.
     *
     * @hide
     */
    abstract class RemovalCallback {
        /**
         * Called when the given face can't be removed.
         *
         * @param fp        The face that the call attempted to remove
         * @param errMsgId  An associated error message id
         * @param errString An error message indicating why the face id can't be removed
         */
        fun onRemovalError(face: Face?, errMsgId: Int, errString: CharSequence?) {}

        /**
         * Called when a given face is successfully removed.
         *
         * @param face The face template that was removed.
         */
        fun onRemovalSucceeded(face: Face?) {}
    }

    /**
     * @hide
     */
    abstract class LockoutResetCallback {
        /**
         * Called when lockout period expired and clients are allowed to listen for face authentication
         * again.
         */
        fun onLockoutReset() {}
    }
}