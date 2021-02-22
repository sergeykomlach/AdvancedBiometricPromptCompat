package org.ifaa.android.manager.face;

public abstract class IFAAFaceManager {
    public abstract void authenticate(int reqId, int flags, AuthenticatorCallback authenticatorCallback);

    public abstract int cancel(int reqId);

    public abstract int getVersion();

    public static abstract class AuthenticatorCallback {
        public void onAuthenticationError(int errorCode) {
        }

        public void onAuthenticationStatus(int status) {
        }

        public void onAuthenticationSucceeded() {
        }

        public void onAuthenticationFailed(int errCode) {
        }
    }
}
