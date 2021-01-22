package dev.skomlach.biometric.compat.engine.internal.face.huawei;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

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
    private FaceRecognizeManager manager = null;
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
    private final Object mAuthenticationLock = new Object();
    //EMUI 10.1.0
    private final HuaweiCodesHelper codeToString = new HuaweiCodesHelper(this);
    private AuthCallback mAuthenticationCallback;
    private final Handler mHandler;

    @SuppressLint("WrongConstant")
    public HuaweiFaceUnlockLegacyModule(BiometricInitListener listener) {
        super(BiometricMethod.FACE_HUAWEI_LEGACY);

        mHandler = new MyHandler(ExecutorHelper.INSTANCE.getHandler().getLooper());
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
        BiometricLoggerImpl.d(getName() + ".onCallbackEvent - : " + "reqId(" + reqId + "), type(" + codeToString.getTypeString(type) + "), code(" + codeToString.getCodeString(code) + "), result(" + codeToString.getErrorCodeString(code, errorCode) + ")");
        if (type == 2) {
            synchronized (HuaweiFaceUnlockLegacyModule.this.mAuthenticationLock) {
                if (HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback != null) {
                    int vendorCode;
                    int error;
                    Integer result;
                    if (1 == code) {
                        if (errorCode == 0) {
                            HuaweiFaceUnlockLegacyModule.this.mHandler.obtainMessage(102).sendToTarget();
                        } else if (3 == errorCode) {
                            HuaweiFaceUnlockLegacyModule.this.mHandler.obtainMessage(103).sendToTarget();
                        } else {
                            vendorCode = errorCode;
                            error = 8;
                            result = (Integer) HuaweiFaceUnlockLegacyModule.mErrorCodeMap.get(errorCode);
                            if (result != null) {
                                error = result;
                            }
                            HuaweiFaceUnlockLegacyModule.this.mHandler.obtainMessage(104, error, vendorCode).sendToTarget();
                        }
                    } else if (3 == code) {
                        vendorCode = errorCode;
                        error = 13;
                        result = (Integer) HuaweiFaceUnlockLegacyModule.mAcquiredCodeMap.get(errorCode);
                        if (result != null) {
                            error = result;
                        }
                        HuaweiFaceUnlockLegacyModule.this.mHandler.obtainMessage(101, error, vendorCode).sendToTarget();
                    }
                }
            }
            if (1 == code && manager.release() != 0) {
                BiometricLoggerImpl.e(getName(), "Authentication release failed.");
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
                return (this.manager.getHardwareSupportType() & 1) != 0;
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName());
            }
        }

        return false;
    }

    @Override
    public boolean hasEnrolled() {
        if (isHardwarePresent()) {
            try {
                BiometricLoggerImpl.d(getName() + ".hasEnrolled - " + manager.getEnrolledFaceIDs().length);
                return manager.getEnrolledFaceIDs().length > 0;
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

                mAuthenticationCallback = new AuthCallback(restartPredicate, cancellationSignal, listener);
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
                            manager.cancelAuthenticate(0);
                        } catch (Throwable e) {
                            BiometricLoggerImpl.e(e, getName() + ": release failed unexpectedly");
                        }
                    }
                });
                if (manager.init() != 0) {
                    mAuthenticationCallback.onAuthenticationError(1, codeToString.getErrorString(1, 0));
                    return;
                }
                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                manager.authenticate(0, 1, null);
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

    private class MyHandler extends Handler {
        private MyHandler(Context context) {
            super(context.getMainLooper());
        }

        private MyHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 101:
                    sendAcquiredResult(msg.arg1, msg.arg2);
                    return;
                case 102:
                    sendAuthenticatedSucceeded();
                    return;
                case 103:
                    sendAuthenticatedFailed();
                    return;
                case 104:
                    sendErrorResult(msg.arg1, msg.arg2);
                    return;
                default:
                    return;
            }
        }

        private void sendErrorResult(int errMsgId, int vendorCode) {
            int clientErrMsgId = errMsgId == 8 ? vendorCode + 1000 : errMsgId;
            synchronized (HuaweiFaceUnlockLegacyModule.this.mAuthenticationLock) {
                if (HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback != null) {
                    HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback.onAuthenticationError(clientErrMsgId, codeToString.getErrorString(errMsgId, vendorCode));
                    HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback = null;
                }
            }
        }

        private void sendAuthenticatedSucceeded() {
            synchronized (HuaweiFaceUnlockLegacyModule.this.mAuthenticationLock) {
                if (HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback != null) {
                    HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback.onAuthenticationSucceeded();
                    HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback = null;
                }
            }
        }

        private void sendAuthenticatedFailed() {
            synchronized (HuaweiFaceUnlockLegacyModule.this.mAuthenticationLock) {
                if (HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback != null) {
                    HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback.onAuthenticationFailed();
                    HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback = null;
                }
            }
        }

        private void sendAcquiredResult(int acquireInfo, int vendorCode) {
            String msg = codeToString.getAcquiredString(acquireInfo, vendorCode);
            if (msg != null) {
                int clientInfo = acquireInfo == 13 ? vendorCode + 1000 : acquireInfo;
                synchronized (HuaweiFaceUnlockLegacyModule.this.mAuthenticationLock) {
                    if (HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback != null) {
//                        HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback.onAuthenticationAcquired(acquireInfo);
                        HuaweiFaceUnlockLegacyModule.this.mAuthenticationCallback.onAuthenticationHelp(clientInfo, msg);
                    }
                }
            }
        }
    }
}