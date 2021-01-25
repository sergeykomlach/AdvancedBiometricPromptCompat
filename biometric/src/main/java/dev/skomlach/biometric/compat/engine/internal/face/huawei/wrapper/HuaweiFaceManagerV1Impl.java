package dev.skomlach.biometric.compat.engine.internal.face.huawei.wrapper;

import android.content.Context;

import dev.skomlach.biometric.compat.engine.BiometricCodes;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

public class HuaweiFaceManagerV1Impl extends HuaweiFaceManagerV1 {

    private static final int FACE_AUTH_VERSION_V1 = 1;
    private static final int HUAWEI_OP_FAIL = -1;
    private static final int HUAWEI_OP_SUCCESS = 0;
    private static final String TAG = "HuaweiFaceManagerV1Impl";

    public HuaweiFaceManagerV1Impl(Context context) {
        HuaweiFaceRecognizeManager.createInstance(context);
    }

    public void authenticate(int reqID, int flag, AuthenticatorCallback callback) {
        HuaweiFaceRecognizeManager frManager = HuaweiFaceRecognizeManager.getInstance();
        if (frManager == null) {
            BiometricLoggerImpl.e(TAG, "HuaweiFaceRecognizeManager is null");
        } else if (callback == null) {
            BiometricLoggerImpl.e(TAG, "callback empty");
        } else {
            frManager.setAuthCallback(callback);
            String str = TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("reqID is ");
            stringBuilder.append(reqID);
            stringBuilder.append("flag is ");
            stringBuilder.append(flag);
            BiometricLoggerImpl.d(str, stringBuilder.toString());
            int ret = frManager.init();
            if (ret != HUAWEI_OP_SUCCESS) {
                String str2 = TAG;
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("init failed returning ");
                stringBuilder2.append(ret);
                BiometricLoggerImpl.e(str2, stringBuilder2.toString());
                callback.onAuthenticationError(BiometricCodes.BIOMETRIC_ERROR_HW_UNAVAILABLE);
                return;
            }
            BiometricLoggerImpl.d(TAG, "authenicating... ");
            HuaweiFaceRecognizeManager.getFRManager().authenticate(reqID, flag, null);
        }
    }

    public int cancel(int reqID) {
        BiometricLoggerImpl.d(TAG, "canceling...");
        if (HuaweiFaceRecognizeManager.getInstance() == null) {
            BiometricLoggerImpl.e(TAG, "HuaweiFaceRecognizeManager is null");
            return -1;
        }
        HuaweiFaceRecognizeManager.getFRManager().cancelAuthenticate(reqID);
        return 0;
    }

    public int getVersion() {
        return 1;
    }

    @Override
    public boolean isHardwareDetected() {
        return (HuaweiFaceRecognizeManager.getFRManager().getHardwareSupportType() & 1) != 0;
    }

    @Override
    public boolean hasEnrolledTemplates() {
        return true;//HuaweiFaceRecognizeManager.getFRManager().getEnrolledFaceIDs().length > 0;
    }
}
