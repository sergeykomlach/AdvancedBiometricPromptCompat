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

package android.hardware.fingerprint

import android.os.CancellationSignal
import android.os.Handler
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.Mac

class FingerprintManager {
    companion object {
        const val FINGERPRINT_ACQUIRED_GOOD = 0
        const val FINGERPRINT_ACQUIRED_PARTIAL = 1
        const val FINGERPRINT_ACQUIRED_INSUFFICIENT = 2
        const val FINGERPRINT_ACQUIRED_IMAGER_DIRTY = 3
        const val FINGERPRINT_ACQUIRED_TOO_SLOW = 4
        const val FINGERPRINT_ACQUIRED_TOO_FAST = 5

        const val FINGERPRINT_ERROR_HW_UNAVAILABLE = 1
        const val FINGERPRINT_ERROR_UNABLE_TO_PROCESS = 2
        const val FINGERPRINT_ERROR_TIMEOUT = 3
        const val FINGERPRINT_ERROR_NO_SPACE = 4
        const val FINGERPRINT_ERROR_CANCELED = 5
        const val FINGERPRINT_ERROR_LOCKOUT = 7
        const val FINGERPRINT_ERROR_VENDOR = 8
        const val FINGERPRINT_ERROR_LOCKOUT_PERMANENT = 9
        const val FINGERPRINT_ERROR_USER_CANCELED = 10
        const val FINGERPRINT_ERROR_NO_FINGERPRINTS = 11
        const val FINGERPRINT_ERROR_HW_NOT_PRESENT = 12
    }

    val isHardwareDetected: Boolean
        get() = false

    fun hasEnrolledFingerprints(): Boolean {
        return false
    }

    fun authenticate(
        crypto: CryptoObject?,
        cancel: CancellationSignal?,
        flags: Int,
        callback: AuthenticationCallback?,
        handler: Handler?
    ) {
        throw IllegalArgumentException("Must supply an authentication callback")
    }

    class CryptoObject {
        var signature: Signature? = null
            private set
        var cipher: Cipher? = null
            private set
        var mac: Mac? = null
            private set

        constructor(signature: Signature?) {
            this.signature = signature
        }

        constructor(cipher: Cipher?) {
            this.cipher = cipher
        }

        constructor(mac: Mac?) {
            this.mac = mac
        }
    }

    class AuthenticationResult(val cryptoObject: CryptoObject?)

    abstract class AuthenticationCallback {
        open fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {}
        open fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {}
        open fun onAuthenticationSucceeded(result: AuthenticationResult?) {}
        open fun onAuthenticationFailed() {}
        open fun onAuthenticationAcquired(acquireInfo: Int) {}
    }
}
