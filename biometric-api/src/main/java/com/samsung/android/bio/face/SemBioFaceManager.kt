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

package com.samsung.android.bio.face

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.view.View
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.Mac

class SemBioFaceManager {
    fun authenticate(
        cryptoObject: CryptoObject?,
        cancellationSignal: CancellationSignal?,
        i: Int,
        authenticationCallback: AuthenticationCallback?,
        handler: Handler?,
        i2: Int,
        bundle: Bundle?,
        view: View?
    ) {
    }

    fun authenticate(
        cryptoObject: CryptoObject?,
        cancellationSignal: CancellationSignal?,
        i: Int,
        authenticationCallback: AuthenticationCallback?,
        handler: Handler?,
        view: View?
    ) {
    }

    val enrolledFaces: List<Face>?
        get() = null

    fun getEnrolledFaces(i: Int): List<Face>? {
        return null
    }

    fun hasEnrolledFaces(): Boolean {
        return false
    }

    val isHardwareDetected: Boolean
        get() = true

    abstract class AuthenticationCallback {
        fun onAuthenticationAcquired(i: Int) {}
        open fun onAuthenticationError(i: Int, charSequence: CharSequence?) {}
        open fun onAuthenticationFailed() {}
        open fun onAuthenticationHelp(i: Int, charSequence: CharSequence?) {}
        open fun onAuthenticationSucceeded(authenticationResult: AuthenticationResult?) {}
    }

    class AuthenticationResult(val cryptoObject: CryptoObject, val face: Face)
    class CryptoObject {
        private val mCrypto: Any
        val fidoRequestData: ByteArray
        var fidoResultData: ByteArray? = null
            private set

        constructor(signature: Signature, bArr: ByteArray) {
            mCrypto = signature
            fidoRequestData = bArr
        }

        constructor(cipher: Cipher, bArr: ByteArray) {
            mCrypto = cipher
            fidoRequestData = bArr
        }

        constructor(mac: Mac, bArr: ByteArray) {
            mCrypto = mac
            fidoRequestData = bArr
        }

        val cipher: Cipher?
            get() = if (mCrypto is Cipher) mCrypto else null

        private fun setFidoResultData(bArr: ByteArray) {
            fidoResultData = bArr
        }

        val mac: Mac?
            get() = if (mCrypto is Mac) mCrypto else null
        val opId: Long
            get() = 0
        val signature: Signature?
            get() = if (mCrypto is Signature) mCrypto else null
    }

    companion object {
        @Synchronized
        @JvmStatic
        fun getInstance(context: Context?): SemBioFaceManager? {
            return null
        }
    }
}