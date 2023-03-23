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

package com.hihonor.android.facerecognition

import android.os.CancellationSignal
import android.os.Handler
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.Mac


abstract class FaceManager {
    abstract fun hasEnrolledTemplates(): Boolean
    abstract val isHardwareDetected: Boolean

    abstract fun authenticate(
        var1: CryptoObject?,
        var2: CancellationSignal?,
        var3: Int,
        var4: AuthenticationCallback?,
        var5: Handler?
    )

    abstract class AuthenticationCallback {
        open fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {}
        open fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {}
        open fun onAuthenticationSucceeded(result: AuthenticationResult?) {}
        open fun onAuthenticationFailed() {}
    }

    class AuthenticationResult(val cryptoObject: CryptoObject)
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

        val signature: Signature?
            get() = if (mCrypto is Signature) mCrypto else null
        val cipher: Cipher?
            get() = if (mCrypto is Cipher) mCrypto else null
        val mac: Mac?
            get() = if (mCrypto is Mac) mCrypto else null
    }

    companion object {
        const val FACE_ERROR_HW_UNAVAILABLE = 1
        const val FACE_ERROR_UNABLE_TO_PROCESS = 2
        const val FACE_ERROR_TIMEOUT = 3
        const val FACE_ERROR_NO_SPACE = 4
        const val FACE_ERROR_CANCELED = 5
        const val FACE_ERROR_UNABLE_TO_REMOVE = 6
        const val FACE_ERROR_LOCKOUT = 7
        const val FACE_ERROR_VENDOR = 8
        const val FACE_ERROR_LOCKOUT_PERMANENT = 9
        const val FACE_ERROR_USER_CANCELED = 10
        const val FACE_ERROR_NOT_ENROLLED = 11
        const val FACE_ERROR_HW_NOT_PRESENT = 12
        const val FACE_ERROR_VENDOR_BASE = 1000
        const val FACE_ACQUIRED_GOOD = 0
        const val FACE_ACQUIRED_INSUFFICIENT = 1
        const val FACE_ACQUIRED_TOO_BRIGHT = 2
        const val FACE_ACQUIRED_TOO_DARK = 3
        const val FACE_ACQUIRED_TOO_CLOSE = 4
        const val FACE_ACQUIRED_TOO_FAR = 5
        const val FACE_ACQUIRED_TOO_HIGH = 6
        const val FACE_ACQUIRED_TOO_LOW = 7
        const val FACE_ACQUIRED_TOO_RIGHT = 8
        const val FACE_ACQUIRED_TOO_LEFT = 9
        const val FACE_ACQUIRED_TOO_MUCH_MOTION = 10
        const val FACE_ACQUIRED_POOR_GAZE = 11
        const val FACE_ACQUIRED_NOT_DETECTED = 12
        const val FACE_ACQUIRED_VENDOR = 13
        private const val TAG = "Facerecognition.FaceManager"
    }
}
