package dev.skomlach.biometric.compat.engine.internal.face.android;

import android.annotation.SuppressLint;
import android.hardware.face.FaceAuthenticationManager;
import android.hardware.face.FaceManager;
import android.os.Build;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import java.util.Collections;
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
public class AndroidFaceUnlockModule extends AbstractBiometricModule {
    private FaceAuthenticationManager faceAuthenticationManager = null;
    private FaceManager faceManager = null;

    @SuppressLint("WrongConstant")
    public AndroidFaceUnlockModule(BiometricInitListener listener) {
        super(BiometricMethod.FACE_ANDROIDAPI);
        Reflection.unseal(getContext(), Collections.singletonList("android.hardware.face"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                faceAuthenticationManager = getContext().getSystemService(FaceAuthenticationManager.class);
            } catch (Throwable ignore) {
                faceAuthenticationManager = null;
            }
        } else {
            try {
                faceAuthenticationManager = (FaceAuthenticationManager) getContext().getSystemService("face");
            } catch (Throwable ignore) {
                faceAuthenticationManager = null;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                faceManager = getContext().getSystemService(FaceManager.class);
            } catch (Throwable ignore) {
                faceManager = null;
            }
        } else {
            try {
                faceManager = (FaceManager) getContext().getSystemService("face");
            } catch (Throwable ignore) {
                faceManager = null;
            }
        }
        if (listener != null) {
            listener
                    .initFinished(getBiometricMethod(), AndroidFaceUnlockModule.this);
        }
    }

    @Override
    public boolean isManagerAccessible() {
        return faceAuthenticationManager != null || faceManager != null;
    }

    @Override
    public boolean isHardwarePresent() {
        boolean faceAuthenticationManagerIsHardwareDetected = false;
        boolean faceManagerIsHardwareDetected = false;
        if (faceAuthenticationManager != null) {
            try {
                faceAuthenticationManagerIsHardwareDetected = faceAuthenticationManager.isHardwareDetected();
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName());
            }
        }
        if (faceManager != null) {
            try {
                faceManagerIsHardwareDetected = faceManager.isHardwareDetected();
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName());
            }
        }
        return faceManagerIsHardwareDetected || faceAuthenticationManagerIsHardwareDetected;
    }

    @Override
    public boolean hasEnrolled() {
        boolean faceAuthenticationManagerHasEnrolled = false;
        boolean faceManagerHasEnrolled = false;
        if (faceAuthenticationManager != null) {
            try {
                faceAuthenticationManagerHasEnrolled =
                        (boolean)faceAuthenticationManager.getClass().getMethod("hasEnrolledFace").invoke(faceAuthenticationManager);
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName());
                try {
                    faceAuthenticationManagerHasEnrolled =
                            (boolean)faceAuthenticationManager.getClass().getMethod("hasEnrolledTemplates").invoke(faceAuthenticationManager);
                } catch (Throwable e2) {
                    BiometricLoggerImpl.e(e2, getName());
                }
            }
        }
        if (faceManager != null) {
            try {
                faceManagerHasEnrolled =
                        (boolean)faceManager.getClass().getMethod("hasEnrolledFace").invoke(faceManager);
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName());
                try {
                    faceManagerHasEnrolled =
                            (boolean)faceManager.getClass().getMethod("hasEnrolledTemplates").invoke(faceManager);
                } catch (Throwable e2) {
                    BiometricLoggerImpl.e(e2, getName());
                }
            }
        }
        return faceAuthenticationManagerHasEnrolled || faceManagerHasEnrolled;
    }

    @Override
    public void authenticate(final CancellationSignal cancellationSignal,
                             final AuthenticationListener listener,
                             final RestartPredicate restartPredicate) throws SecurityException {

        BiometricLoggerImpl.d(getName() + ".authenticate - " + getBiometricMethod().toString());
        // Why getCancellationSignalObject returns an Object is unexplained
        final android.os.CancellationSignal signalObject = cancellationSignal == null ? null :
                (android.os.CancellationSignal) cancellationSignal.getCancellationSignalObject();
        try {
            if (signalObject == null)
                throw new IllegalArgumentException("CancellationSignal cann't be null");
            if (ExecutorHelper.INSTANCE.getExecutor() == null)
                throw new IllegalArgumentException("Executor cann't be null");
            if (ExecutorHelper.INSTANCE.getHandler() == null)
                throw new IllegalArgumentException("Handler cann't be null");

            return;
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e, getName() + ": authenticate failed unexpectedly");
            if (listener != null) {
                listener.onFailure(AuthenticationFailureReason.UNKNOWN, tag());
            }
        }

        if (faceAuthenticationManager != null && faceAuthenticationManager.isHardwareDetected() && faceAuthenticationManager.hasEnrolledFace()) {
            try {
                // Occasionally, an NPE will bubble up out of FaceAuthenticationManager.authenticate
                faceAuthenticationManager.authenticate(null, signalObject, 0,
                        new FaceAuthenticationManagerAuthCallback(restartPredicate, cancellationSignal, listener), ExecutorHelper.INSTANCE.getHandler());
                return;
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName() + ": authenticate failed unexpectedly");
            }
        }
        else if (faceManager != null && faceManager.isHardwareDetected() && faceManager.hasEnrolledTemplates()) {
            try {
                // Occasionally, an NPE will bubble up out of FaceAuthenticationManager.authenticate
                faceManager.authenticate(null, signalObject, 0,
                        new FaceManagerAuthCallback(restartPredicate, cancellationSignal, listener), ExecutorHelper.INSTANCE.getHandler());
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

    class FaceManagerAuthCallback extends FaceManager.AuthenticationCallback {

        private final RestartPredicate restartPredicate;
        private final CancellationSignal cancellationSignal;
        private final AuthenticationListener listener;

        public FaceManagerAuthCallback(RestartPredicate restartPredicate,
                                       CancellationSignal cancellationSignal, AuthenticationListener listener) {
            this.restartPredicate = restartPredicate;
            this.cancellationSignal = cancellationSignal;
            this.listener = listener;
        }

        @Override
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
                    Core.cancelAuthentication(AndroidFaceUnlockModule.this);
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
            BiometricLoggerImpl.d(getName() + ".onAuthenticationHelp: " + CodeToString.getHelpCode(helpMsgId) + "-" + helpString);
            if (listener != null) {
                listener.onHelp(AuthenticationHelpReason.getByCode(helpMsgId), helpString);
            }
        }

        @Override
        public void onAuthenticationSucceeded(FaceManager.AuthenticationResult result) {
            BiometricLoggerImpl.d(getName() + ".onAuthenticationSucceeded: " + result);
            if (listener != null) {
                listener.onSuccess(tag());
            }
        }

        @Override
        public void onAuthenticationFailed() {
            BiometricLoggerImpl.d(getName() + ".onAuthenticationFailed: ");
            if (listener != null) {
                listener.onFailure(AuthenticationFailureReason.AUTHENTICATION_FAILED, tag());
            }
        }
    }

    class FaceAuthenticationManagerAuthCallback extends FaceAuthenticationManager.AuthenticationCallback {

        private final RestartPredicate restartPredicate;
        private final CancellationSignal cancellationSignal;
        private final AuthenticationListener listener;

        public FaceAuthenticationManagerAuthCallback(RestartPredicate restartPredicate,
                                                     CancellationSignal cancellationSignal, AuthenticationListener listener) {
            this.restartPredicate = restartPredicate;
            this.cancellationSignal = cancellationSignal;
            this.listener = listener;
        }

        @Override
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
                    Core.cancelAuthentication(AndroidFaceUnlockModule.this);
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
            BiometricLoggerImpl.d(getName() + ".onAuthenticationHelp: " + CodeToString.getHelpCode(helpMsgId) + "-" + helpString);
            if (listener != null) {
                listener.onHelp(AuthenticationHelpReason.getByCode(helpMsgId), helpString);
            }
        }

        @Override
        public void onAuthenticationSucceeded(FaceAuthenticationManager.AuthenticationResult result) {
            BiometricLoggerImpl.d(getName() + ".onAuthenticationSucceeded: " + result);
            if (listener != null) {
                listener.onSuccess(tag());
            }
        }

        @Override
        public void onAuthenticationFailed() {
            BiometricLoggerImpl.d(getName() + ".onAuthenticationFailed: ");
            if (listener != null) {
                listener.onFailure(AuthenticationFailureReason.AUTHENTICATION_FAILED, tag());
            }
        }
    }
}