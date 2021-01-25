package dev.skomlach.biometric.compat.engine.internal.face.huawei.wrapper;

import android.content.Context;

import com.huawei.facerecognition.FaceRecognizeManager;
import com.huawei.facerecognition.FaceRecognizeManager.FaceRecognizeCallback;

import dev.skomlach.biometric.compat.engine.BiometricCodes;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

public class HuaweiFaceRecognizeManager {
    public static final int DEFAULT_FLAG = 1;

    public static final int CODE_CALLBACK_ACQUIRE = 3;
    public static final int CODE_CALLBACK_BUSY = 4;
    public static final int CODE_CALLBACK_CANCEL = 2;
    public static final int CODE_CALLBACK_OUT_OF_MEM = 5;
    public static final int CODE_CALLBACK_RESULT = 1;

    public static final int HUAWEI_FACE_AUTHENTICATOR_FAIL = 103;
    public static final int HUAWEI_FACE_AUTHENTICATOR_SUCCESS = 100;
//
//    public static final int HUAWEI_FACE_AUTH_ERROR_CANCEL = 102;
//    public static final int HUAWEI_FACE_AUTH_ERROR_LOCKED = 129;
//    public static final int HUAWEI_FACE_AUTH_ERROR_TIMEOUT = 113;

    public static final int HUAWEI_FACE_AUTH_STATUS_BRIGHT = 406;
    public static final int HUAWEI_FACE_AUTH_STATUS_DARK = 405;
    public static final int HUAWEI_FACE_AUTH_STATUS_EYE_CLOSED = 403;
    public static final int HUAWEI_FACE_AUTH_STATUS_FACE_OFFET_BOTTOM = 412;
    public static final int HUAWEI_FACE_AUTH_STATUS_FACE_OFFET_LEFT = 409;
    public static final int HUAWEI_FACE_AUTH_STATUS_FACE_OFFET_RIGHT = 410;
    public static final int HUAWEI_FACE_AUTH_STATUS_FACE_OFFET_TOP = 411;
    public static final int HUAWEI_FACE_AUTH_STATUS_FAR_FACE = 404;
    public static final int HUAWEI_FACE_AUTH_STATUS_INSUFFICIENT = 402;
    public static final int HUAWEI_FACE_AUTH_STATUS_MOUTH_OCCLUSION = 408;
    public static final int HUAWEI_FACE_AUTH_STATUS_PARTIAL = 401;
    public static final int HUAWEI_FACE_AUTH_STATUS_QUALITY = 407;

    public static final String TAG = "HuaweiFaceRecognize";
    public static final int TYPE_CALLBACK_AUTH = 2;
    private static HuaweiFaceRecognizeManager mHuaweiFrManager = null;
    private static FaceRecognizeManager mManager = null;
    private HuaweiFaceManager.AuthenticatorCallback mAuthenticatorCallback = null;
    private final FaceRecognizeCallback mFRCallback = new FaceRecognizeCallback() {
        public void onCallbackEvent(int reqId, int type, int code, int errorCode) {
            String str = HuaweiFaceRecognizeManager.TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(" onCallbackEvent gotten reqId ");
            stringBuilder.append(reqId);
            stringBuilder.append(" type ");
            stringBuilder.append(type);
            stringBuilder.append(" code ");
            stringBuilder.append(code);
            stringBuilder.append(" errCode ");
            stringBuilder.append(errorCode);
            BiometricLoggerImpl.d(str, stringBuilder.toString());
            if (HuaweiFaceRecognizeManager.this.mAuthenticatorCallback == null) {
                BiometricLoggerImpl.e(HuaweiFaceRecognizeManager.TAG, "mAuthenticatorCallback empty in onCallbackEvent ");
                HuaweiFaceRecognizeManager.this.release();
                return;
            }
            if (type != TYPE_CALLBACK_AUTH) {
                str = HuaweiFaceRecognizeManager.TAG;
                stringBuilder = new StringBuilder();
                stringBuilder.append(" gotten not huawei's auth callback reqid ");
                stringBuilder.append(reqId);
                stringBuilder.append(" type ");
                stringBuilder.append(type);
                stringBuilder.append(" code ");
                stringBuilder.append(code);
                stringBuilder.append(" errCode ");
                stringBuilder.append(errorCode);
                BiometricLoggerImpl.e(str, stringBuilder.toString());
            } else if (code == CODE_CALLBACK_CANCEL) {
                int result = HuaweiFaceRecognizeManager.converHwErrorCodeToHuawei(errorCode);
                String str2 = HuaweiFaceRecognizeManager.TAG;
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append(" result ");
                stringBuilder2.append(result);
                BiometricLoggerImpl.d(str2, stringBuilder2.toString());
                if (result != HUAWEI_FACE_AUTHENTICATOR_FAIL) {
                    HuaweiFaceRecognizeManager.this.mAuthenticatorCallback.onAuthenticationError(result);
                } else {
                    HuaweiFaceRecognizeManager.this.mAuthenticatorCallback.onAuthenticationFailed();
                    str2 = HuaweiFaceRecognizeManager.TAG;
                    stringBuilder2 = new StringBuilder();
                    stringBuilder2.append(" fail reason ");
                    stringBuilder2.append(result);
                    BiometricLoggerImpl.e(str2, stringBuilder2.toString());
                }
                HuaweiFaceRecognizeManager.this.release();
            } else if (code == CODE_CALLBACK_ACQUIRE) {
                int result = HuaweiFaceRecognizeManager.converHwAcquireInfoToHuawei(errorCode);
                String str2 = HuaweiFaceRecognizeManager.TAG;
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append(" result ");
                stringBuilder2.append(result);
                BiometricLoggerImpl.d(str2, stringBuilder2.toString());
                if (result != HUAWEI_FACE_AUTHENTICATOR_FAIL) {
                    HuaweiFaceRecognizeManager.this.mAuthenticatorCallback.onAuthenticationStatus(result);
                } else {
                    HuaweiFaceRecognizeManager.this.mAuthenticatorCallback.onAuthenticationFailed();
                    str2 = HuaweiFaceRecognizeManager.TAG;
                    stringBuilder2 = new StringBuilder();
                    stringBuilder2.append(" fail reason ");
                    stringBuilder2.append(result);
                    BiometricLoggerImpl.e(str2, stringBuilder2.toString());
                }
            } else if (code == CODE_CALLBACK_RESULT) {
                int result = HuaweiFaceRecognizeManager.converHwErrorCodeToHuawei(errorCode);
                String str2 = HuaweiFaceRecognizeManager.TAG;
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append(" result ");
                stringBuilder2.append(result);
                BiometricLoggerImpl.d(str2, stringBuilder2.toString());
                if (result == HUAWEI_FACE_AUTHENTICATOR_SUCCESS) {
                    BiometricLoggerImpl.d(HuaweiFaceRecognizeManager.TAG, "huawei face auth success");
                    HuaweiFaceRecognizeManager.this.mAuthenticatorCallback.onAuthenticationSucceeded();
                } else if (result != HUAWEI_FACE_AUTHENTICATOR_FAIL) {
                    HuaweiFaceRecognizeManager.this.mAuthenticatorCallback.onAuthenticationError(result);
                } else {
                    HuaweiFaceRecognizeManager.this.mAuthenticatorCallback.onAuthenticationFailed();
                    str2 = HuaweiFaceRecognizeManager.TAG;
                    stringBuilder2 = new StringBuilder();
                    stringBuilder2.append(" fail reason ");
                    stringBuilder2.append(result);
                    BiometricLoggerImpl.e(str2, stringBuilder2.toString());
                }
                HuaweiFaceRecognizeManager.this.release();
            }
        }
    };

    public HuaweiFaceRecognizeManager(Context context) {
        if (mManager == null) {
            mManager = new FaceRecognizeManager(context, this.mFRCallback);
        }
    }

    public static int converHwAcquireInfoToHuawei(int hwAcquireInfo) {
        String str = TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" converHwhwAcquireInfoToHuawei hwAcquireInfo is ");
        stringBuilder.append(hwAcquireInfo);
        BiometricLoggerImpl.e(str, stringBuilder.toString());
        if (hwAcquireInfo == 0) {
            return HUAWEI_FACE_AUTHENTICATOR_SUCCESS;
        }
        if (hwAcquireInfo == 22) {
            return HUAWEI_FACE_AUTH_STATUS_EYE_CLOSED;
        }
        switch (hwAcquireInfo) {
            case 4://HuaweiManagerV2.HUAWEI_AUTH_FACE /*4*/:
                return HUAWEI_FACE_AUTH_STATUS_QUALITY;
            case 5:
            case 6:
                return HUAWEI_FACE_AUTH_STATUS_INSUFFICIENT;
            case 7:
                return HUAWEI_FACE_AUTH_STATUS_FAR_FACE;
            case 8://HuaweiManagerV2.HUAWEI_AUTH_PIN /*8*/:
                return HUAWEI_FACE_AUTH_STATUS_FACE_OFFET_LEFT;
            case 9:
                return HUAWEI_FACE_AUTH_STATUS_FACE_OFFET_TOP;
            case 10:
                return HUAWEI_FACE_AUTH_STATUS_FACE_OFFET_RIGHT;
            case 11:
                return HUAWEI_FACE_AUTH_STATUS_FACE_OFFET_BOTTOM;
            default:
                switch (hwAcquireInfo) {
                    case 29:
                        return HUAWEI_FACE_AUTH_STATUS_PARTIAL;
                    case 30:
                        return HUAWEI_FACE_AUTH_STATUS_DARK;
                    case 31:
                        return HUAWEI_FACE_AUTH_STATUS_BRIGHT;
                    default:
                        return HUAWEI_FACE_AUTHENTICATOR_FAIL;
                }
        }
    }

    public static int converHwErrorCodeToHuawei(int hwErrorCode) {
        String str = TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" converHwErrorCodeToHuawei hwErrorCode is ");
        stringBuilder.append(hwErrorCode);
        BiometricLoggerImpl.e(str, stringBuilder.toString());
        if (hwErrorCode == FaceRecognizeManager.FaceErrorCode.SUCCESS) {
            return HUAWEI_FACE_AUTHENTICATOR_SUCCESS;
        }

        if (hwErrorCode == FaceRecognizeManager.FaceErrorCode.COMPARE_FAIL ||
                hwErrorCode == FaceRecognizeManager.FaceErrorCode.FAILED ||
                hwErrorCode == FaceRecognizeManager.FaceErrorCode.NO_FACE_DATA ||
                hwErrorCode == FaceRecognizeManager.FaceErrorCode.OVER_MAX_FACES)
            return HUAWEI_FACE_AUTHENTICATOR_FAIL;

        if (hwErrorCode == FaceRecognizeManager.FaceErrorCode.CANCELED)
            return BiometricCodes.BIOMETRIC_ERROR_CANCELED;
        if (hwErrorCode == FaceRecognizeManager.FaceErrorCode.TIMEOUT)
            return BiometricCodes.BIOMETRIC_ERROR_TIMEOUT;
        if (hwErrorCode == FaceRecognizeManager.FaceErrorCode.IN_LOCKOUT_MODE)
            return BiometricCodes.BIOMETRIC_ERROR_LOCKOUT;

        return BiometricCodes.BIOMETRIC_ERROR_VENDOR;
    }

    public static synchronized void createInstance(Context context) {
        synchronized (HuaweiFaceRecognizeManager.class) {
            if (mHuaweiFrManager == null) {
                mHuaweiFrManager = new HuaweiFaceRecognizeManager(context);
            }
        }
    }

    public static HuaweiFaceRecognizeManager getInstance() {
        return mHuaweiFrManager;
    }

    public static FaceRecognizeManager getFRManager() {
        return mManager;
    }

    public int init() {
        if (mManager != null) {
            return mManager.init();
        }
        return -1;
    }

    public void release() {
        if (mManager != null) {
            mManager.release();
        }
    }

    public void setAuthCallback(HuaweiFaceManager.AuthenticatorCallback authCallback) {
        this.mAuthenticatorCallback = authCallback;
    }
}
