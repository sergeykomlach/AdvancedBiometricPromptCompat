package android.hardware.face;

import android.os.CancellationSignal;
import android.os.Handler;

import java.security.Signature;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;

public class FaceManager {

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