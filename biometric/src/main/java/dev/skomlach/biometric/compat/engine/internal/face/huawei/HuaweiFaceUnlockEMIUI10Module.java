package dev.skomlach.biometric.compat.engine.internal.face.huawei;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import com.huawei.facerecognition.FaceManager;
import com.huawei.facerecognition.HwFaceManagerFactory;
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

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class HuaweiFaceUnlockEMIUI10Module extends AbstractBiometricModule {
    //EMUI 10.1.0
    private FaceManager manager = null;

    public HuaweiFaceUnlockEMIUI10Module(BiometricInitListener listener) {
        super(BiometricMethod.FACE_HUAWEI_EMUI_10.getId());

        try {
            manager = HwFaceManagerFactory.getFaceManager(getContext());
        } catch (Throwable ignore) {
            manager = null;
        }

        if (listener != null) {
            listener
                    .initFinished(BiometricMethod.FACE_HUAWEI_EMUI_10, HuaweiFaceUnlockEMIUI10Module.this);
        }
    }
    //[HuaweiFaceUnlockEMIUI10Module.onAuthenticationError: BIOMETRIC_ERROR_HW_UNAVAILABLE-face_error_hw_not_available]

    @Override
    public boolean isManagerAccessible() {
        return manager != null;
    }

    @Override
    public boolean isHardwarePresent() {
        if (manager != null) {
            try {
                if (manager.isHardwareDetected()) {
                    return true;
                }
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e);
            }
        }

        return false;
    }

    @Override
    public boolean hasEnrolled() {
        if (manager != null) {
            try {
                return manager.isHardwareDetected() && manager.hasEnrolledTemplates();
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e);
            }
        }

        return false;
    }

    @Override
    public void authenticate(final CancellationSignal cancellationSignal,
                             final AuthenticationListener listener,
                             final RestartPredicate restartPredicate) throws SecurityException {

        for (BiometricMethod method : BiometricMethod.values()) {
            if (method.getId() == tag()) {
                BiometricLoggerImpl.d("HuaweiFaceUnlockEMIUI10Module.authenticate - " + method.toString());
            }
        }

        if (!isHardwarePresent()) {
            if (listener != null) {
                listener.onFailure(AuthenticationFailureReason.HARDWARE_UNAVAILABLE, tag());
            }
            return;
        }
        if (!hasEnrolled()) {
            if (listener != null) {
                listener.onFailure(AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED, tag());
            }
            return;
        }

        if (manager != null) {
            try {

                final FaceManager.AuthenticationCallback callback =
                        new AuthCallback(restartPredicate, cancellationSignal, listener);

                // Why getCancellationSignalObject returns an Object is unexplained
                final android.os.CancellationSignal signalObject = cancellationSignal == null ? null :
                        (android.os.CancellationSignal) cancellationSignal.getCancellationSignalObject();

                if (signalObject == null)
                    throw new IllegalArgumentException("CancellationSignal cann't be null");
                if (ExecutorHelper.INSTANCE.getExecutor() == null)
                    throw new IllegalArgumentException("Executor cann't be null");
                if (ExecutorHelper.INSTANCE.getHandler() == null)
                    throw new IllegalArgumentException("Handler cann't be null");

                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                manager.authenticate(null, signalObject, 0, callback, ExecutorHelper.INSTANCE.getHandler());
                return;
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, "HuaweiFaceUnlockEMIUI10Module: authenticate failed unexpectedly");
            }
        }

        if (listener != null) {
            listener.onFailure(AuthenticationFailureReason.UNKNOWN, tag());
        }
        return;
    }

    class AuthCallback extends FaceManager.AuthenticationCallback {

        private final RestartPredicate restartPredicate;
        private final CancellationSignal cancellationSignal;
        private final AuthenticationListener listener;

        public AuthCallback(RestartPredicate restartPredicate,
                            CancellationSignal cancellationSignal, AuthenticationListener listener) {
            this.restartPredicate = restartPredicate;
            this.cancellationSignal = cancellationSignal;
            this.listener = listener;
        }

        @Override
        public void onAuthenticationError(int errMsgId, CharSequence errString) {
            BiometricLoggerImpl.d("HuaweiFaceUnlockEMIUI10Module.onAuthenticationError: " + CodeToString.getErrorCode(errMsgId) + "-" + errString);
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
                    BiometricErrorLockoutPermanentFix.INSTANCE.setBiometricSensorPermanentlyLocked();
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
                    Core.cancelAuthentication(HuaweiFaceUnlockEMIUI10Module.this);
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

        @Override
        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            BiometricLoggerImpl.d("HuaweiFaceUnlockEMIUI10Module.onAuthenticationHelp: " + CodeToString.getHelpCode(helpMsgId) + "-" + helpString);
            if (listener != null) {
                listener.onHelp(AuthenticationHelpReason.getByCode(helpMsgId), helpString.toString());
            }
        }

        @Override
        public void onAuthenticationSucceeded(FaceManager.AuthenticationResult result) {
            BiometricLoggerImpl.d("HuaweiFaceUnlockEMIUI10Module.onAuthenticationSucceeded: " + result);
            if (listener != null) {
                listener.onSuccess(tag());
            }
        }

        @Override
        public void onAuthenticationFailed() {
            BiometricLoggerImpl.d("HuaweiFaceUnlockEMIUI10Module.onAuthenticationFailed: ");
            if (listener != null) {
                listener.onFailure(AuthenticationFailureReason.AUTHENTICATION_FAILED, tag());
            }
        }
    }
}