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

package android.hardware.face

import android.os.CancellationSignal
import android.os.Handler
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.Mac

class FaceManager {
    class CryptoObject {
        val signature: Signature?
            get() = null
        val cipher: Cipher?
            get() = null
        val mac: Mac?
            get() = null
    }

    fun authenticate(
        crypto: CryptoObject?,
        cancel: CancellationSignal?,
        flags: Int,
        callback: AuthenticationCallback?,
        handler: Handler?
    ) {
    }

    fun authenticate(
        crypto: CryptoObject?,
        cancel: CancellationSignal?,
        flags: Int,
        callback: AuthenticationCallback?,
        handler: Handler?,
        userId: Int
    ) {
        throw IllegalArgumentException("Must supply an authentication callback")
    }

    fun getEnrolledFaces(userId: Int): List<Face>? {
        return null
    }

    val enrolledFaces: List<Face>?
        get() = getEnrolledFaces(0)

    fun hasEnrolledTemplates(): Boolean {
        return false
    }

    fun hasEnrolledTemplates(userId: Int): Boolean {
        return false
    }

    val isHardwareDetected: Boolean
        get() = false

    class AuthenticationResult(val cryptoObject: CryptoObject, val face: Face, val userId: Int)
    abstract class AuthenticationCallback {
        open fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {}
        open fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {}
        open fun onAuthenticationSucceeded(result: AuthenticationResult?) {}
        open fun onAuthenticationFailed() {}
        fun onAuthenticationAcquired(acquireInfo: Int) {}
        fun onProgressChanged(progressInfo: Int) {}
    }
}