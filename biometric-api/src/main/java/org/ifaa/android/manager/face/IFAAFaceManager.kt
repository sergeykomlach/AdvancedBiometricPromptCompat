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

package org.ifaa.android.manager.face

abstract class IFAAFaceManager {
    abstract fun authenticate(reqId: Int, flags: Int, authenticatorCallback: AuthenticatorCallback?)
    abstract fun cancel(reqId: Int): Int
    abstract val version: Int

    abstract class AuthenticatorCallback {
        fun onAuthenticationError(errorCode: Int) {}
        fun onAuthenticationStatus(status: Int) {}
        fun onAuthenticationSucceeded() {}
        fun onAuthenticationFailed(errCode: Int) {}
    }
}