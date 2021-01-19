package android.hardware.miuiface;

import android.graphics.Rect;
import android.graphics.RectF;
import android.os.CancellationSignal;
import android.os.Handler;
import android.view.Surface;
import java.util.List;

public interface IMiuiFaceManager {
    public static final int TEMPLATE_INVALIDATE = -1;
    public static final int TEMPLATE_NONE = 0;
    public static final int TEMPLATE_SERVICE_NOT_INIT = -2;
    public static final int TEMPLATE_VALIDATE = 1;

    public static abstract class AuthenticationCallback {
        public void onAuthenticationError(int errorCode, CharSequence errString) {
        }

        public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
        }

        public void onAuthenticationSucceeded(Miuiface face) {
        }

        public void onAuthenticationFailed() {
        }
    }

    public static abstract class EnrollmentCallback {
        public void onEnrollmentError(int errMsgId, CharSequence errString) {
        }

        public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) {
        }

        public void onEnrollmentProgress(int remaining, int faceId) {
        }
    }

    public static abstract class LockoutResetCallback {
        public void onLockoutReset() {
        }
    }

    public static abstract class RemovalCallback {
        public void onRemovalError(Miuiface face, int errMsgId, CharSequence errString) {
        }

        public void onRemovalSucceeded(Miuiface face, int remaining) {
        }
    }

    void addLockoutResetCallback(LockoutResetCallback lockoutResetCallback);

    void authenticate(CancellationSignal cancellationSignal, int i, AuthenticationCallback authenticationCallback, Handler handler, int i2);

    void enroll(byte[] bArr, CancellationSignal cancellationSignal, int i, EnrollmentCallback enrollmentCallback, Surface surface, Rect rect, int i2);

    void enroll(byte[] bArr, CancellationSignal cancellationSignal, int i, EnrollmentCallback enrollmentCallback, Surface surface, RectF rectF, RectF rectF2, int i2);

    int extCmd(int i, int i2);

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

    void rename(int i, String str);

    void resetTimeout(byte[] bArr);
}