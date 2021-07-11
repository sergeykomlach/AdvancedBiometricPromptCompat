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

import android.os.CancellationSignal;
import android.os.Handler;

import java.security.Signature;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;

public class FaceManager {

    public void authenticate(CryptoObject crypto, CancellationSignal cancel, int flags, AuthenticationCallback callback, Handler handler) {

    }

    public void authenticate(CryptoObject crypto, CancellationSignal cancel, int flags, AuthenticationCallback callback, Handler handler, int userId) {
        throw new IllegalArgumentException("Must supply an authentication callback");
    }

    public List<Face> getEnrolledFaces(int userId) {
        return null;
    }

    public List<Face> getEnrolledFaces() {
        return getEnrolledFaces(0);
    }

    public boolean hasEnrolledTemplates() {
        return false;
    }

    public boolean hasEnrolledTemplates(int userId) {
        return false;
    }

    public boolean isHardwareDetected() {
        return false;
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

    public static class AuthenticationResult {
        private final CryptoObject mObject;
        private final Face mFace;
        private final int mUserId;

        public AuthenticationResult(CryptoObject crypto, Face face, int userId) {
            this.mObject = crypto;
            this.mFace = face;
            this.mUserId = userId;
        }

        public CryptoObject getCryptoObject() {
            return this.mObject;
        }

        public Face getFace() {
            return this.mFace;
        }

        public int getUserId() {
            return this.mUserId;
        }
    }

    public static abstract class AuthenticationCallback {

        public void onAuthenticationError(int errorCode, CharSequence errString) {
        }

        public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
        }

        public void onAuthenticationSucceeded(AuthenticationResult result) {
        }

        public void onAuthenticationFailed() {
        }

        public void onAuthenticationAcquired(int acquireInfo) {
        }

        public void onProgressChanged(int progressInfo) {
        }
    }
}