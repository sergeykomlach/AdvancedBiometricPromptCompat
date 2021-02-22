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
package android.hardware.iris

import android.os.CancellationSignal
import android.os.Handler
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.Mac

class IrisManager {
    fun authenticate(
        crypto: CryptoObject?, cancel: CancellationSignal?,
        flags: Int, callback: AuthenticationCallback, handler: Handler?
    ) {
    }

    fun authenticate(
        crypto: CryptoObject?, cancel: CancellationSignal?,
        flags: Int, callback: AuthenticationCallback, handler: Handler?, userId: Int
    ) {
    }

    fun getEnrolledIrises(userId: Int): List<Iris>? {
        return null
    }

    /**
     * Obtain the list of enrolled iris templates.
     *
     * @return list of current iris items
     * @hide
     */
    val enrolledIrises: List<Iris>?
        get() = null

    /**
     * Determine if there is at least one iris enrolled.
     *
     * @return true if at least one iris is enrolled, false otherwise
     */
    fun hasEnrolledIrises(): Boolean {
        return false
    }

    val isHardwareDetected: Boolean
        get() = false

    /**
     * A wrapper class for the crypto objects supported by IrisManager. Currently the
     * framework supports [Signature], [Cipher] and [Mac] objects.
     */
    class CryptoObject {
        private val mCrypto: Any

        constructor(signature: Signature) {
            mCrypto = signature
        }

        constructor(cipher: Cipher) {
            mCrypto = cipher
        }

        constructor(mac: Mac) {
            mCrypto = mac
        }

        /**
         * Get [Signature] object.
         *
         * @return [Signature] object or null if this doesn't contain one.
         */
        val signature: Signature?
            get() = if (mCrypto is Signature) mCrypto else null

        /**
         * Get [Cipher] object.
         *
         * @return [Cipher] object or null if this doesn't contain one.
         */
        val cipher: Cipher?
            get() = if (mCrypto is Cipher) mCrypto else null

        /**
         * Get [Mac] object.
         *
         * @return [Mac] object or null if this doesn't contain one.
         */
        val mac: Mac?
            get() = if (mCrypto is Mac) mCrypto else null

        /**
         * @return the opId associated with this object or 0 if none
         * @hide
         */
        val opId: Long
            get() = 0
    }

    /**
     * Container for callback data from [IrisManager.authenticate].
     */
    class AuthenticationResult
    /**
     * Authentication result
     *
     * @param crypto the crypto object
     * @param iris   the recognized iris data, if allowed.
     * @hide
     */(
        /**
         * Obtain the crypto object associated with this transaction
         *
         * @return crypto object provided to [IrisManager.authenticate].
         */
        val cryptoObject: CryptoObject,
        /**
         * Obtain the Iris associated with this operation. Applications are strongly
         * discouraged from associating specific iris with specific applications or operations.
         *
         * @hide
         */
        val iris: Iris,
        /**
         * Obtain the userId for which this iris was authenticated.
         *
         * @hide
         */
        val userId: Int
    )

    /**
     * Callback structure provided to [IrisManager.authenticate]. Users of [ ][IrisManager.authenticate] must provide an implementation of this for listening to
     * iris events.
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
         * Called when a iris is recognized.
         *
         * @param result An object containing authentication-related data
         */
        open fun onAuthenticationSucceeded(result: AuthenticationResult?) {}

        /**
         * Called when a iris is valid but not recognized.
         */
        open fun onAuthenticationFailed() {}

        /**
         * Called when a iris image has been acquired, but wasn't processed yet.
         *
         * @param acquireInfo one of IRIS_ACQUIRED_* constants
         * @hide
         */
        fun onAuthenticationAcquired(acquireInfo: Int) {}
    }
}