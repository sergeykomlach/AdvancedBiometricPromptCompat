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
package android.hardware.iris;

import android.os.CancellationSignal;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.Signature;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;

public class IrisManager {

    public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
                             int flags, @NonNull AuthenticationCallback callback, @Nullable Handler handler) {

    }

    public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
                             int flags, @NonNull AuthenticationCallback callback, Handler handler, int userId) {

    }

    public List<Iris> getEnrolledIrises(int userId) {
        return null;
    }

    /**
     * Obtain the list of enrolled iris templates.
     *
     * @return list of current iris items
     * @hide
     */

    public List<Iris> getEnrolledIrises() {
        return null;
    }

    /**
     * Determine if there is at least one iris enrolled.
     *
     * @return true if at least one iris is enrolled, false otherwise
     */

    public boolean hasEnrolledIrises() {
        return false;
    }

    public boolean isHardwareDetected() {

        return false;
    }

    /**
     * A wrapper class for the crypto objects supported by IrisManager. Currently the
     * framework supports {@link Signature}, {@link Cipher} and {@link Mac} objects.
     */
    public static final class CryptoObject {

        private final Object mCrypto;

        public CryptoObject(@NonNull Signature signature) {
            mCrypto = signature;
        }

        public CryptoObject(@NonNull Cipher cipher) {
            mCrypto = cipher;
        }

        public CryptoObject(@NonNull Mac mac) {
            mCrypto = mac;
        }

        /**
         * Get {@link Signature} object.
         *
         * @return {@link Signature} object or null if this doesn't contain one.
         */
        public Signature getSignature() {
            return mCrypto instanceof Signature ? (Signature) mCrypto : null;
        }

        /**
         * Get {@link Cipher} object.
         *
         * @return {@link Cipher} object or null if this doesn't contain one.
         */
        public Cipher getCipher() {
            return mCrypto instanceof Cipher ? (Cipher) mCrypto : null;
        }

        /**
         * Get {@link Mac} object.
         *
         * @return {@link Mac} object or null if this doesn't contain one.
         */
        public Mac getMac() {
            return mCrypto instanceof Mac ? (Mac) mCrypto : null;
        }

        /**
         * @return the opId associated with this object or 0 if none
         * @hide
         */
        public long getOpId() {
            return 0;
        }
    }

    /**
     * Container for callback data from {@link IrisManager#authenticate(CryptoObject,
     * CancellationSignal, int, AuthenticationCallback, Handler)}.
     */
    public static class AuthenticationResult {
        private final Iris mIris;
        private final CryptoObject mCryptoObject;
        private final int mUserId;

        /**
         * Authentication result
         *
         * @param crypto the crypto object
         * @param iris   the recognized iris data, if allowed.
         * @hide
         */
        public AuthenticationResult(CryptoObject crypto, Iris iris, int userId) {
            mCryptoObject = crypto;
            mIris = iris;
            mUserId = userId;
        }

        /**
         * Obtain the crypto object associated with this transaction
         *
         * @return crypto object provided to {@link IrisManager#authenticate(CryptoObject,
         * CancellationSignal, int, AuthenticationCallback, Handler)}.
         */
        public CryptoObject getCryptoObject() {
            return mCryptoObject;
        }

        /**
         * Obtain the Iris associated with this operation. Applications are strongly
         * discouraged from associating specific iris with specific applications or operations.
         *
         * @hide
         */
        public Iris getIris() {
            return mIris;
        }

        /**
         * Obtain the userId for which this iris was authenticated.
         *
         * @hide
         */
        public int getUserId() {
            return mUserId;
        }
    }

    /**
     * Callback structure provided to {@link IrisManager#authenticate(CryptoObject,
     * CancellationSignal, int, AuthenticationCallback, Handler)}. Users of {@link
     * IrisManager#authenticate(CryptoObject, CancellationSignal,
     * int, AuthenticationCallback, Handler) } must provide an implementation of this for listening to
     * iris events.
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
         * Called when a iris is recognized.
         *
         * @param result An object containing authentication-related data
         */
        public void onAuthenticationSucceeded(AuthenticationResult result) {
        }

        /**
         * Called when a iris is valid but not recognized.
         */
        public void onAuthenticationFailed() {
        }

        /**
         * Called when a iris image has been acquired, but wasn't processed yet.
         *
         * @param acquireInfo one of IRIS_ACQUIRED_* constants
         * @hide
         */
        public void onAuthenticationAcquired(int acquireInfo) {
        }
    }
}