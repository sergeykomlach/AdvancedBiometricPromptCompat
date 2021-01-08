package dev.skomlach.biometric.compat.engine.internal.face.soter;

import android.annotation.SuppressLint;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import com.tencent.soter.core.biometric.BiometricManagerCompat;
import com.tencent.soter.core.model.ConstantsSoter;

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
public class SoterFaceUnlockModule extends AbstractBiometricModule {

    private final BiometricInitListener listener;
    private BiometricManagerCompat manager;

    @SuppressLint("WrongConstant")
    public SoterFaceUnlockModule(final BiometricInitListener listener) {
        super(BiometricMethod.FACE_SOTERAPI);

        this.listener = listener;
        try {
            manager = BiometricManagerCompat.from(getContext(), ConstantsSoter.FACEID_AUTH);
        } catch (Throwable ignore) {
            manager = null;
        }
        if (listener != null) {
            listener
                    .initFinished(getBiometricMethod(), SoterFaceUnlockModule.this);
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
                if (manager.isHardwareDetected()) {
                    return true;
                }
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
                return manager.isHardwareDetected() && manager.hasEnrolledBiometric();
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

                final BiometricManagerCompat.AuthenticationCallback callback =
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
                manager.authenticate(null, 0, signalObject, callback, ExecutorHelper.INSTANCE.getHandler());
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

    class AuthCallback extends BiometricManagerCompat.AuthenticationCallback {

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
                    Core.cancelAuthentication(SoterFaceUnlockModule.this);
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
                listener.onHelp(AuthenticationHelpReason.getByCode(helpMsgId), helpString.toString());
            }
        }

        @Override
        public void onAuthenticationSucceeded(BiometricManagerCompat.AuthenticationResult result) {
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