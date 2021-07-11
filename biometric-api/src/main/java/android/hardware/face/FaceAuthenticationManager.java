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
package android.hardware.face;
/**
 * Copyright (C) 2014 The Android Open Source Project
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.os.CancellationSignal;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;

/**
 * A class that coordinates access to the face authentication hardware.
 */
public class FaceAuthenticationManager {

    /**
     * Request authentication of a crypto object. This call operates the face recognition sensor
     * and starts capturing images. It terminates when
     * {@link AuthenticationCallback#onAuthenticationError(int, CharSequence)} or
     * {@link AuthenticationCallback#onAuthenticationSucceeded(AuthenticationResult)} is called, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     *
     * @param crypto   object associated with the call or null if none required.
     * @param cancel   an object that can be used to cancel authentication
     * @param flags    optional flags; should be 0
     * @param callback an object to receive authentication events
     * @param handler  an optional handler to handle callback events
     * @throws IllegalArgumentException if the crypto operation is not supported or is not backed
     *                                  by <a href="{@docRoot}training/articles/keystore.html">Android Keystore
     *                                  facility</a>.
     * @throws IllegalStateException    if the crypto primitive is not initialized.
     */

    public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
                             int flags, @NonNull AuthenticationCallback callback, @Nullable Handler handler) {
    }

    /**
     * Per-user version
     *
     * @hide
     */

    public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
                             int flags, @NonNull AuthenticationCallback callback, Handler handler, int userId) {
    }

    /**
     * Request face authentication enrollment. This call operates the face recognition sensor
     * and starts capturing images. Progress will be indicated by callbacks to the
     * {@link EnrollmentCallback} object. It terminates when
     * {@link EnrollmentCallback#onEnrollmentError(int, CharSequence)} or
     * {@link EnrollmentCallback#onEnrollmentProgress(int) is called with remaining == 0, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     *
     * @param token    a unique token provided by a recent creation or verification of device
     *                 credentials (e.g. pin, pattern or password).
     * @param cancel   an object that can be used to cancel enrollment
     * @param flags    optional flags
     * @param userId   the user to whom this face will belong to
     * @param callback an object to receive enrollment events
     * @hide
     */

    public void enroll(byte[] token, CancellationSignal cancel, int flags,
                       int userId, EnrollmentCallback callback) {
    }

    /**
     * Requests a pre-enrollment auth token to tie enrollment to the confirmation of
     * existing device credentials (e.g. pin/pattern/password).
     *
     * @hide
     */

    public long preEnroll() {
        return 0;
    }

    /**
     * Finishes enrollment and cancels the current auth token.
     *
     * @hide
     */

    public int postEnroll() {
        return 0;
    }

    /**
     * Sets the active user. This is meant to be used to select the current profile for enrollment
     * to allow separate enrolled faces for a work profile
     *
     * @param userId
     * @hide
     */

    public void setActiveUser(int userId) {

    }

    /**
     * Remove given face template from face hardware and/or protected storage.
     *
     * @param face     the face item to remove
     * @param userId   the user who this face belongs to
     * @param callback an optional callback to verify that face templates have been
     *                 successfully removed. May be null if no callback is required.
     * @hide
     */

    public void remove(Face face, int userId, RemovalCallback callback) {
    }

    /**
     * Obtain the enrolled face template.
     *
     * @return the current face item
     * @hide
     */

    public Face getEnrolledFace(int userId) {
        return null;
    }

    /**
     * Obtain the enrolled face template.
     *
     * @return the current face item
     * @hide
     */

    public Face getEnrolledFace() {
        return null;
    }

    /**
     * Determine if there is a face enrolled.
     *
     * @return true if a face is enrolled, false otherwise
     */

    public boolean hasEnrolledFace() {
        return false;
    }

    /**
     * @hide
     */
    public boolean hasEnrolledFace(int userId) {
        return false;
    }

    /**
     * Determine if face authentication sensor hardware is present and functional.
     *
     * @return true if hardware is present and functional, false otherwise.
     */

    public boolean isHardwareDetected() {
        return false;
    }

    /**
     * Retrieves the authenticator token for binding keys to the lifecycle
     * of the calling user's face. Used only by internal clients.
     *
     * @hide
     */
    public long getAuthenticatorId() {
        return 0;
    }

    public static final class CryptoObject {

        public Signature getSignature() {
            return null;
        }

        public Cipher getCipher() {
            return null;
        }

        public Mac getMac() {
            return null;
        }
    }

    /**
     * Container for callback data from {@link FaceAuthenticationManager#authenticate(CryptoObject,
     * CancellationSignal, int, AuthenticationCallback, Handler)}.
     */
    public static class AuthenticationResult {

        /**
         * Authentication result
         *
         * @param crypto the crypto object
         * @param face   the recognized face data, if allowed.
         * @hide
         */
        public AuthenticationResult(CryptoObject crypto, Face face, int userId) {
        }

        /**
         * Obtain the crypto object associated with this transaction
         *
         * @return crypto object provided to {@link FaceAuthenticationManager#authenticate(CryptoObject,
         * CancellationSignal, int, AuthenticationCallback, Handler)}.
         */
        public CryptoObject getCryptoObject() {
            return null;
        }

        /**
         * Obtain the Face associated with this operation. Applications are strongly
         * discouraged from associating specific faces with specific applications or operations.
         *
         * @hide
         */
        public Face getFace() {
            return null;
        }

        /**
         * Obtain the userId for which this face was authenticated.
         *
         * @hide
         */
        public int getUserId() {
            return 0;
        }
    }

    /**
     * Callback structure provided to {@link FaceAuthenticationManager#authenticate(CryptoObject,
     * CancellationSignal, int, AuthenticationCallback, Handler)}. Users of {@link
     * FaceAuthenticationManager#authenticate(CryptoObject, CancellationSignal,
     * int, AuthenticationCallback, Handler) } must provide an implementation of this for listening
     * to face events.
     */
    public static abstract class AuthenticationCallback {

        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further callbacks will be made on this object.
         *
         * @param errorCode An integer identifying the error message
         * @param errString A human-readable error string that can be shown in UI
         */
        public void onAuthenticationError(int errorCode, CharSequence errString) {
        }

        /**
         * Called when a recoverable error has been encountered during authentication. The help
         * string is provided to give the user guidance for what went wrong, such as
         * "Sensor dirty, please clean it."
         *
         * @param helpCode   An integer identifying the error message
         * @param helpString A human-readable string that can be shown in UI
         */
        public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
        }

        /**
         * Called when a face is recognized.
         *
         * @param result An object containing authentication-related data
         */
        public void onAuthenticationSucceeded(AuthenticationResult result) {
        }

        /**
         * Called when a face is detected but not recognized.
         */
        public void onAuthenticationFailed() {
        }

        /**
         * Called when a face image has been acquired, but wasn't processed yet.
         *
         * @param acquireInfo one of FACE_ACQUIRED_* constants
         * @hide
         */
        public void onAuthenticationAcquired(int acquireInfo) {
        }
    }

    /**
     * Callback structure provided to
     * must provide an implementation of this to  for listening to face enrollment events.
     *
     * @hide
     */
    public static abstract class EnrollmentCallback {

        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further callbacks will be made on this object.
         *
         * @param errMsgId  An integer identifying the error message
         * @param errString A human-readable error string that can be shown in UI
         */
        public void onEnrollmentError(int errMsgId, CharSequence errString) {
        }

        /**
         * Called when a recoverable error has been encountered during enrollment. The help
         * string is provided to give the user guidance for what went wrong, such as
         * "Sensor dirty, please clean it" or what they need to do next, such as
         * "Rotate face up / down."
         *
         * @param helpMsgId  An integer identifying the error message
         * @param helpString A human-readable string that can be shown in UI
         */
        public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) {
        }

        /**
         * Called as each enrollment step progresses. Enrollment is considered complete when
         * remaining reaches 0. This function will not be called if enrollment fails. See
         * {@link EnrollmentCallback#onEnrollmentError(int, CharSequence)}
         *
         * @param remaining The number of remaining steps
         */
        public void onEnrollmentProgress(int remaining) {
        }
    }

    /**
     * Callback structure provided to {@link #remove}. Users of {@link FaceAuthenticationManager} may
     * optionally provide an implementation of this to
     * {@link #remove(Face, int, RemovalCallback)} for listening to face template
     * removal events.
     *
     * @hide
     */
    public static abstract class RemovalCallback {

        /**
         * Called when the given face can't be removed.
         *
         * @param fp        The face that the call attempted to remove
         * @param errMsgId  An associated error message id
         * @param errString An error message indicating why the face id can't be removed
         */
        public void onRemovalError(Face face, int errMsgId, CharSequence errString) {
        }

        /**
         * Called when a given face is successfully removed.
         *
         * @param face The face template that was removed.
         */
        public void onRemovalSucceeded(Face face) {
        }
    }

    /**
     * @hide
     */
    public static abstract class LockoutResetCallback {

        /**
         * Called when lockout period expired and clients are allowed to listen for face authentication
         * again.
         */
        public void onLockoutReset() {
        }
    }
}