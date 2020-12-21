package android.hardware.miuiface;

import android.graphics.Rect;
import android.graphics.RectF;
import android.os.CancellationSignal;
import android.os.Handler;
import android.view.Surface;

import java.util.List;

public interface IMiuiFaceManager {
    int TEMPLATE_INVALIDATE = -1;
    int TEMPLATE_NONE = 0;
    int TEMPLATE_SERVICE_NOT_INIT = -2;
    int TEMPLATE_VALIDATE = 1;

    void addLockoutResetCallback(LockoutResetCallback lockoutResetCallback);

    void authenticate(CancellationSignal cancel, int flags, AuthenticationCallback callback, Handler handler, int timeout);

    public void enroll(byte[] cryptoToken, CancellationSignal cancel, int flags, EnrollmentCallback enrollCallback, Surface surface, Rect detectArea, int timeout);

    public void enroll(byte[] cryptoToken, CancellationSignal cancel, int flags, EnrollmentCallback enrollCallback, Surface surface, RectF detectArea, RectF enrollArea, int timeout);

    int extCmd(int cmd, int param);

    List<Miuiface> getEnrolledFaces();

    int getManagerVersion();

    String getVendorInfo();

    int hasEnrolledFaces();

    boolean isFaceFeatureSupport();

    boolean isFaceUnlockInited();

    boolean isReleased();

    boolean isSupportScreenOnDelayed();

    void preInitAuthen();

    void release();

    void remove(Miuiface miuiface, RemovalCallback removalCallback);

    void rename(int faceId, String name);

    void resetTimeout(byte[] token);

    abstract class AuthenticationCallback {
        public void onAuthenticationError(int errorCode, CharSequence errString) {
        }

        public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
        }

        public void onAuthenticationSucceeded(Miuiface face) {
        }

        public void onAuthenticationFailed() {
        }
    }

    abstract class EnrollmentCallback {
        public void onEnrollmentError(int errMsgId, CharSequence errString) {
        }

        public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) {
        }

        public void onEnrollmentProgress(int remaining, int faceId) {
        }
    }

    abstract class LockoutResetCallback {
        public void onLockoutReset() {
        }
    }

    abstract class RemovalCallback {
        public void onRemovalError(Miuiface face, int errMsgId, CharSequence errString) {
        }

        public void onRemovalSucceeded(Miuiface face, int remaining) {
        }
    }
}