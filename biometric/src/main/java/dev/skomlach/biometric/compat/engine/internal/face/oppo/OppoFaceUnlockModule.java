package dev.skomlach.biometric.compat.engine.internal.face.oppo;

import android.annotation.SuppressLint;
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
public class OppoFaceUnlockModule extends AbstractBiometricModule {
    //https://github.com/dstmath/OppoFramework/blob/ab8096fb1fc84325f2b7095353b58756465535ca/A92s_10_0_0/src/main/java/android/hardware/face/FaceManager.java
    private FaceManager manager = null;

    @SuppressLint("WrongConstant")
    public OppoFaceUnlockModule(BiometricInitListener listener) {
        super(BiometricMethod.FACE_OPPO);
        Reflection.unseal(getContext(), Collections.singletonList("android.hardware.face"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                manager = getContext().getSystemService(FaceManager.class);
            } catch (Throwable ignore) {
                manager = null;
            }
        } else {
            try {
                manager = (FaceManager) getContext().getSystemService("face");
            } catch (Throwable ignore) {
                manager = null;
            }
        }
        if (listener != null) {
            listener
                    .initFinished(getBiometricMethod(), OppoFaceUnlockModule.this);
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
               return manager.isHardwareDetected();
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
                return manager.isHardwareDetected() && manager.hasEnrolledTemplates();
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

                // Occasionally, an NPE will bubble up out of SomeManager.authenticate

                manager.authenticate(null, signalObject, 0, callback, ExecutorHelper.INSTANCE.getHandler());

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
                    Core.cancelAuthentication(OppoFaceUnlockModule.this);
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
}