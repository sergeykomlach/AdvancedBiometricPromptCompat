package dev.skomlach.biometric.compat.engine.internal.face.huawei;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import com.huawei.facerecognition.FaceRecognizeManager;

import java.util.Collections;
import java.util.HashMap;

import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.AuthenticationHelpReason;
import dev.skomlach.biometric.compat.engine.BiometricInitListener;
import dev.skomlach.biometric.compat.engine.BiometricMethod;
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule;
import dev.skomlach.biometric.compat.engine.internal.core.Core;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate;
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix;
import dev.skomlach.biometric.compat.utils.CodeToString;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.misc.ExecutorHelper;
import me.weishu.reflection.Reflection;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class HuaweiFaceUnlockLegacyModule extends AbstractBiometricModule implements FaceRecognizeManager.FaceRecognizeCallback {
    private static final HashMap<Integer, Integer> mAcquiredCodeMap = new HashMap<Integer, Integer>() {
        {
            put(Integer.valueOf(0), Integer.valueOf(0));
            put(Integer.valueOf(1), Integer.valueOf(13));
            put(Integer.valueOf(2), Integer.valueOf(13));
            put(Integer.valueOf(3), Integer.valueOf(1));
            put(Integer.valueOf(4), Integer.valueOf(1));
            put(Integer.valueOf(5), Integer.valueOf(12));
            put(Integer.valueOf(6), Integer.valueOf(5));
            put(Integer.valueOf(7), Integer.valueOf(4));
            put(Integer.valueOf(8), Integer.valueOf(9));
            put(Integer.valueOf(9), Integer.valueOf(6));
            put(Integer.valueOf(10), Integer.valueOf(8));
            put(Integer.valueOf(11), Integer.valueOf(7));
            put(Integer.valueOf(12), Integer.valueOf(13));
            put(Integer.valueOf(13), Integer.valueOf(13));
            put(Integer.valueOf(14), Integer.valueOf(13));
            put(Integer.valueOf(15), Integer.valueOf(13));
            put(Integer.valueOf(16), Integer.valueOf(13));
            put(Integer.valueOf(17), Integer.valueOf(13));
            put(Integer.valueOf(18), Integer.valueOf(13));
            put(Integer.valueOf(19), Integer.valueOf(10));
            put(Integer.valueOf(20), Integer.valueOf(13));
            put(Integer.valueOf(21), Integer.valueOf(11));
            put(Integer.valueOf(22), Integer.valueOf(11));
            put(Integer.valueOf(23), Integer.valueOf(13));
            put(Integer.valueOf(27), Integer.valueOf(13));
            put(Integer.valueOf(28), Integer.valueOf(10));
            put(Integer.valueOf(29), Integer.valueOf(13));
            put(Integer.valueOf(30), Integer.valueOf(13));
            put(Integer.valueOf(31), Integer.valueOf(13));
            put(Integer.valueOf(32), Integer.valueOf(13));
        }
    };
    private static final HashMap<Integer, Integer> mErrorCodeMap = new HashMap<Integer, Integer>() {
        {
            put(Integer.valueOf(1), Integer.valueOf(8));
            put(Integer.valueOf(2), Integer.valueOf(5));
            put(Integer.valueOf(3), Integer.valueOf(8));
            put(Integer.valueOf(4), Integer.valueOf(3));
            put(Integer.valueOf(5), Integer.valueOf(2));
            put(Integer.valueOf(6), Integer.valueOf(2));
            put(Integer.valueOf(7), Integer.valueOf(4));
            put(Integer.valueOf(8), Integer.valueOf(7));
            put(Integer.valueOf(9), Integer.valueOf(8));
            put(Integer.valueOf(10), Integer.valueOf(11));
            put(Integer.valueOf(11), Integer.valueOf(2));
        }
    };
    //EMUI 10.1.0
    private FaceRecognizeManager manager = null;
    private AuthCallback authCallback;

    @SuppressLint("WrongConstant")
    public HuaweiFaceUnlockLegacyModule(BiometricInitListener listener) {
        super(BiometricMethod.FACE_HUAWEI_LEGACY);
        Reflection.unseal(getContext(), Collections.singletonList("com.huawei.facerecognition"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                manager = getContext().getSystemService(FaceRecognizeManager.class);
            } catch (Throwable ignore) {
                manager = null;
            }
        } else {
            try {
                manager = (FaceRecognizeManager) getContext().getSystemService("facerecognition");
            } catch (Throwable ignore) {
                manager = null;
            }
        }
        if (manager == null)
            try {
                manager = new FaceRecognizeManager(getContext(), this);
            } catch (Throwable ignore) {
                manager = null;
            }

        BiometricLoggerImpl.d(getName() + ".manager - " + manager);

        if (listener != null) {
            listener
                    .initFinished(getBiometricMethod(), HuaweiFaceUnlockLegacyModule.this);
        }
    }

    @Override
    public void onCallbackEvent(int reqId, int type, int code, int errorCode) {
        //[HuaweiFaceUnlockLegacyModule.onCallbackEvent - reqId: 1; type:2; code:2; errorCode:0]
        BiometricLoggerImpl.d(getName() + ".onCallbackEvent - : " + "reqId(" + reqId + "), type(" + getTypeString(type) + "), code(" + getCodeString(code) + "), result(" + getErrorCodeString(code, errorCode) + ")");
        if (authCallback == null) {
            manager.release();
            return;
        }
        if (type == FaceRecognizeManager.TYPE_CALLBACK_AUTH) {
            int vendorCode;
            int error;
            Integer result;
            if (FaceRecognizeManager.CODE_CALLBACK_RESULT == code) {
                if (errorCode == 0) {
                    if (authCallback != null)
                        authCallback.onAuthenticationSucceeded();
                    manager.release();
                } else if (3 == errorCode) {
                    if (authCallback != null)
                        authCallback.onAuthenticationFailed();
                } else {
                    vendorCode = errorCode;
                    error = 8;
                    result = (Integer) mErrorCodeMap.get(errorCode);
                    if (result != null) {
                        error = result.intValue();
                    }
                    if (authCallback != null)
                        authCallback.onAuthenticationError(error, getErrorString(error, vendorCode));
                    manager.release();
                }
            } else if (FaceRecognizeManager.CODE_CALLBACK_ACQUIRE == code) {
                vendorCode = errorCode;
                error = 13;
                result = (Integer) mAcquiredCodeMap.get(errorCode);
                if (result != null) {
                    error = result.intValue();
                }
                if (authCallback != null)
                    authCallback.onAuthenticationHelp(error, getAcquiredString(error, vendorCode));
            }
            else {
                vendorCode = errorCode;
                error = 8;
                result = (Integer) mErrorCodeMap.get(errorCode);
                if (result != null) {
                    error = result.intValue();
                }
                if (authCallback != null)
                    authCallback.onAuthenticationError(error, getErrorString(error, vendorCode));
                manager.release();
            }
        }
    }
    @Override
    public boolean isManagerAccessible() {
        return manager != null;
    }

    @Override
    public boolean isHardwarePresent() {
        if (manager != null) {
            try {
                BiometricLoggerImpl.d(getName() + ".isHardwarePresent - " + manager.getHardwareSupportType());
                return manager.getHardwareSupportType() != -1;
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName());
            }
        }

        return false;
    }

    @Override
    public boolean hasEnrolled() {
        if (manager != null) {
            try {
                BiometricLoggerImpl.d(getName() + ".hasEnrolled - " + manager.getEnrolledFaceIDs().length);
                return manager.getHardwareSupportType() != -1 && manager.getEnrolledFaceIDs().length > 0;
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName());
            }
        }

        return false;
    }

    @Override
    public void authenticate(final CancellationSignal cancellationSignal,
                             final AuthenticationListener listener,
                             final RestartPredicate restartPredicate) throws SecurityException {

        BiometricLoggerImpl.d(getName() + ".authenticate - " + getBiometricMethod().toString());

        if (manager != null) {
            try {

                authCallback = new AuthCallback(restartPredicate, cancellationSignal, listener);
                // Why getCancellationSignalObject returns an Object is unexplained
                final android.os.CancellationSignal signalObject = cancellationSignal == null ? null :
                        (android.os.CancellationSignal) cancellationSignal.getCancellationSignalObject();

                if (signalObject == null)
                    throw new IllegalArgumentException("CancellationSignal cann't be null");
                if (ExecutorHelper.INSTANCE.getExecutor() == null)
                    throw new IllegalArgumentException("Executor cann't be null");
                if (ExecutorHelper.INSTANCE.getHandler() == null)
                    throw new IllegalArgumentException("Handler cann't be null");
                signalObject.setOnCancelListener(new android.os.CancellationSignal.OnCancelListener() {
                    @Override
                    public void onCancel() {
                        try {
                            manager.cancelAuthenticate(1);
                        } catch (Throwable e) {
                            BiometricLoggerImpl.e(e, getName() + ": release failed unexpectedly");
                        }
                    }
                });
                if (manager.init() != 0)
                    throw new IllegalStateException();
                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                manager.authenticate(1, 1, null);
                return;
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName() + ": authenticate failed unexpectedly");
            }
        }

        if (listener != null) {
            listener.onFailure(AuthenticationFailureReason.UNKNOWN, tag());
        }
        return;
    }

    private class AuthCallback {

        private final RestartPredicate restartPredicate;
        private final CancellationSignal cancellationSignal;
        private final AuthenticationListener listener;

        public AuthCallback(RestartPredicate restartPredicate,
                            CancellationSignal cancellationSignal, AuthenticationListener listener) {
            this.restartPredicate = restartPredicate;
            this.cancellationSignal = cancellationSignal;
            this.listener = listener;
        }

        public void onAuthenticationError(int errMsgId, CharSequence errString) {
            BiometricLoggerImpl.d(getName() + ".onAuthenticationError: " + CodeToString.getErrorCode(errMsgId) + "-" + errString);
            AuthenticationFailureReason failureReason = AuthenticationFailureReason.UNKNOWN;
            switch (errMsgId) {
                case BIOMETRIC_ERROR_NO_BIOMETRICS:
                    failureReason = AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED;
                    break;
                case BIOMETRIC_ERROR_HW_NOT_PRESENT:
                    failureReason = AuthenticationFailureReason.NO_HARDWARE;
                    break;
                case BIOMETRIC_ERROR_HW_UNAVAILABLE:
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE;
                    break;
                case BIOMETRIC_ERROR_LOCKOUT_PERMANENT:
                    BiometricErrorLockoutPermanentFix.INSTANCE.setBiometricSensorPermanentlyLocked(getBiometricMethod().getBiometricType());
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE;
                    break;
                case BIOMETRIC_ERROR_UNABLE_TO_PROCESS:
                case BIOMETRIC_ERROR_NO_SPACE:
                    failureReason = AuthenticationFailureReason.SENSOR_FAILED;
                    break;
                case BIOMETRIC_ERROR_TIMEOUT:
                    failureReason = AuthenticationFailureReason.TIMEOUT;
                    break;
                case BIOMETRIC_ERROR_LOCKOUT:
                    lockout();
                    failureReason = AuthenticationFailureReason.LOCKED_OUT;
                    break;
                case BIOMETRIC_ERROR_USER_CANCELED:
                    Core.cancelAuthentication(HuaweiFaceUnlockLegacyModule.this);
                    return;
                case BIOMETRIC_ERROR_CANCELED:
                    // Don't send a cancelled message.
                    return;
            }

            if (restartPredicate.invoke(failureReason)) {
                if (listener != null) {
                    listener.onFailure(failureReason, tag());
                }
                authenticate(cancellationSignal, listener, restartPredicate);
            } else {
                switch (failureReason) {
                    case SENSOR_FAILED:
                    case AUTHENTICATION_FAILED:
                        lockout();
                        failureReason = AuthenticationFailureReason.LOCKED_OUT;
                        break;
                }

                if (listener != null) {
                    listener.onFailure(failureReason, tag());
                }
            }
        }

        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            BiometricLoggerImpl.d(getName() + ".onAuthenticationHelp: " + CodeToString.getHelpCode(helpMsgId) + "-" + helpString);
            if (listener != null) {
                listener.onHelp(AuthenticationHelpReason.getByCode(helpMsgId), helpString.toString());
            }
        }

        public void onAuthenticationSucceeded() {
            BiometricLoggerImpl.d(getName() + ".onAuthenticationSucceeded: ");
            if (listener != null) {
                listener.onSuccess(tag());
            }
        }

        public void onAuthenticationFailed() {
            BiometricLoggerImpl.d(getName() + ".onAuthenticationFailed: ");
            if (listener != null) {
                listener.onFailure(AuthenticationFailureReason.AUTHENTICATION_FAILED, tag());
            }
        }
    }


    private String getTypeString(int type) {
        switch (type) {
            case 1:
                return "ENROLL";
            case 2:
                return "AUTH";
            case 3:
                return "REMOVE";
            default:
                return "" + type;
        }
    }

    private static String getCodeString(int code) {
        switch (code) {
            case 1:
                return "result";
            case 2:
                return "cancel";
            case 3:
                return "acquire";
            case 4:
                return "request busy";
            default:
                return "" + code;
        }
    }

    private String getErrorCodeString(int code, int errorCode) {
        if (code != 1) {
            if (code == 3) {
                switch (errorCode) {
                    case 4:
                        return "bad quality";
                    case 5:
                        return "no face detected";
                    case 6:
                        return "face too small";
                    case 7:
                        return "face too large";
                    case 8:
                        return "offset left";
                    case 9:
                        return "offset top";
                    case 10:
                        return "offset right";
                    case 11:
                        return "offset bottom";
                    case 13:
                        return "aliveness warning";
                    case 14:
                        return "aliveness failure";
                    case 15:
                        return "rotate left";
                    case 16:
                        return "face rise to high";
                    case 17:
                        return "rotate right";
                    case 18:
                        return "face too low";
                    case 19:
                        return "keep still";
                    case 21:
                        return "eyes occlusion";
                    case 22:
                        return "eyes closed";
                    case 23:
                        return "mouth occlusion";
                    case 27:
                        return "multi faces";
                    case 28:
                        return "face blur";
                    case 29:
                        return "face not complete";
                    case 30:
                        return "too dark";
                    case 31:
                        return "too light";
                    case 32:
                        return "half shadow";
                    default:
                        break;
                }
            }
        }
        switch (errorCode) {
            case 0:
                return "success";
            case 1:
                return "failed";
            case 2:
                return "cancelled";
            case 3:
                return "compare fail";
            case 4:
                return "time out";
            case 5:
                return "invoke init first";
            case 6:
                return "hal invalid";
            case 7:
                return "over max faces";
            case 8:
                return "in lockout mode";
            case 9:
                return "invalid parameters";
            case 10:
                return "no face data";
            case 11:
                return "low temp & cap";
        }
        return "" + errorCode;
    }

    private String getErrorString(int errMsg, int vendorCode) {
        switch (errMsg) {
            case 1:
                return "face_error_hw_not_available";
            case 2:
                return "face_error_unable_to_process";
            case 3:
                return "face_error_timeout";
            case 4:
                return "face_error_no_space";
            case 5:
                return "face_error_canceled";
            case 7:
                return "face_error_lockout";
            case 8:
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("face_error_vendor: code ");
                stringBuilder.append(vendorCode);
                return stringBuilder.toString();
            case 9:
                return "face_error_lockout_permanent";
            case 11:
                return "face_error_not_enrolled";
            case 12:
                return "face_error_hw_not_present";
            default:
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("Invalid error message: ");
                stringBuilder2.append(errMsg);
                stringBuilder2.append(", ");
                stringBuilder2.append(vendorCode);
                BiometricLoggerImpl.e(getName(), stringBuilder2.toString());
                return null;
        }
    }

    private String getAcquiredString(int acquireInfo, int vendorCode) {
        switch (acquireInfo) {
            case 0:
                return null;
            case 1:
                return "face_acquired_insufficient";
            case 2:
                return "face_acquired_too_bright";
            case 3:
                return "face_acquired_too_dark";
            case 4:
                return "face_acquired_too_close";
            case 5:
                return "face_acquired_too_far";
            case 6:
                return "face_acquired_too_high";
            case 7:
                return "face_acquired_too_low";
            case 8:
                return "face_acquired_too_right";
            case 9:
                return "face_acquired_too_left";
            case 10:
                return "face_acquired_too_much_motion";
            case 11:
                return "face_acquired_poor_gaze";
            case 12:
                return "face_acquired_not_detected";
            case 13:
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("face_acquired_vendor: code ");
                stringBuilder.append(vendorCode);
                return stringBuilder.toString();
            default:
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("Invalid acquired message: ");
                stringBuilder2.append(acquireInfo);
                stringBuilder2.append(", ");
                stringBuilder2.append(vendorCode);
                BiometricLoggerImpl.e(getName(), stringBuilder2.toString());
                return null;
        }
    }

}