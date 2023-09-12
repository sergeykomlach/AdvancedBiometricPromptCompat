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

package android.hardware.biometrics

import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.Mac

class CryptoObject {

    private var mSignature: Signature? = null
    private var mCipher: Cipher? = null
    private var mMac: Mac? = null

    constructor(signature: Signature?) {
        mSignature = signature
        mCipher = null
        mMac = null
    }

    constructor(cipher: Cipher?) {
        mCipher = cipher
        mSignature = null
        mMac = null
    }

    constructor(mac: Mac?) {
        mMac = mac
        mCipher = null
        mSignature = null
    }

    /**
     * Get [Signature] object.
     *
     * @return [Signature] object or null if this doesn't contain one.
     */
    fun getSignature(): Signature? {
        return mSignature
    }

    /**
     * Get [Cipher] object.
     *
     * @return [Cipher] object or null if this doesn't contain one.
     */
    fun getCipher(): Cipher? {
        return mCipher
    }

    /**
     * Get [Mac] object.
     *
     * @return [Mac] object or null if this doesn't contain one.
     */
    fun getMac(): Mac? {
        return mMac
    }
}