package android.hardware.face;

import android.os.CancellationSignal;
import android.os.Handler;

import java.util.List;

public class OppoMirrorFaceManager {

    public void authenticate(Object crypto, CancellationSignal cancel, int flags, AuthenticationCallback callback, Handler handler) {

    }

    public void authenticate(Object crypto, CancellationSignal cancel, int flags, AuthenticationCallback callback, Handler handler, int userId) {
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
        private final Object mObject;
        private final Face mFace;
        private final int mUserId;

        public AuthenticationResult(Object crypto, Face face, int userId) {
            this.mObject = crypto;
            this.mFace = face;
            this.mUserId = userId;
        }

        public Object getObject() {
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