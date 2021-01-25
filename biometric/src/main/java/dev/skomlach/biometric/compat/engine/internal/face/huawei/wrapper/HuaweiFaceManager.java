package dev.skomlach.biometric.compat.engine.internal.face.huawei.wrapper;

public abstract class HuaweiFaceManager {

    public abstract void authenticate(int reqId, int flag, AuthenticatorCallback authenticatorCallback);

    public abstract int cancel(int i);

    public abstract int getVersion();

    public abstract boolean isHardwareDetected();

    public abstract boolean hasEnrolledTemplates();

    public static abstract class AuthenticatorCallback {
        public void onAuthenticationError(int errorCode) {
        }

        public void onAuthenticationStatus(int status) {
        }

        public void onAuthenticationSucceeded() {
        }

        public void onAuthenticationFailed() {
        }
    }
}
