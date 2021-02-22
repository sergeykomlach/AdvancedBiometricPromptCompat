package dev.skomlach.biometric.compat.engine.internal.face.miui;

import android.annotation.SuppressLint;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.AuthenticationHelpReason;
import dev.skomlach.biometric.compat.engine.BiometricInitListener;
import dev.skomlach.biometric.compat.engine.BiometricMethod;
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule;
import dev.skomlach.biometric.compat.engine.internal.core.Core;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate;
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.IMiuiFaceManager;
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.MiuiFaceFactory;
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.Miuiface;
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix;
import dev.skomlach.biometric.compat.utils.CodeToString;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.misc.ExecutorHelper;
import me.weishu.reflection.Reflection;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class MiuiFaceUnlockModule extends AbstractBiometricModule {
    private IMiuiFaceManager manager = null;

    @SuppressLint("WrongConstant")
    public MiuiFaceUnlockModule(final BiometricInitListener listener) {
        super(BiometricMethod.FACE_MIUI);

        List<String> list = new ArrayList<>();
        list.add("android.miui");
        list.add("miui.os");
        list.add("miui.util");
        list.add("android.util");
        Reflection.unseal(getContext(), list);
        try {
            manager = MiuiFaceFactory.getFaceManager(getContext(), MiuiFaceFactory.TYPE_3D);
            if (!manager.isFaceFeatureSupport()) {
                throw new RuntimeException("Miui 3DFace not supported");
            }
        } catch (Throwable e1) {
            BiometricLoggerImpl.e(e1, e1.getMessage(), getName());
            try {
                manager = MiuiFaceFactory.getFaceManager(getContext(), MiuiFaceFactory.TYPE_2D);
                if (!manager.isFaceFeatureSupport()) {
                    throw new RuntimeException("Miui 2DFace not supported");
                }
            } catch (Throwable e2) {
                BiometricLoggerImpl.e(e2, e2.getMessage(), getName());
                manager = null;
            }
        }
        BiometricLoggerImpl.e("MiuiFaceUnlockModule - " + manager);

        if (listener != null) {
            listener
                    .initFinished(getBiometricMethod(), MiuiFaceUnlockModule.this);
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
                return manager.isFaceFeatureSupport();
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
                return manager.isFaceFeatureSupport() && manager.getEnrolledFaces().size() > 0;
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

                final IMiuiFaceManager.AuthenticationCallback callback =
                        new AuthCallback(restartPredicate, cancellationSignal, listener);

                // Why getCancellationSignalObject returns an Object is unexplained
                final android.os.CancellationSignal signalObject = cancellationSignal == null ? null :
                        (android.os.CancellationSignal) cancellationSignal.getCancellationSignalObject();

                if (signalObject == null)
                    throw new IllegalArgumentException("CancellationSignal cann't be null");


                if (!manager.isFaceUnlockInited())
                    manager.preInitAuthen();
                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                manager.authenticate(signalObject, 0, callback, ExecutorHelper.INSTANCE.getHandler(), (int) TimeUnit.SECONDS.toMillis(30));
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

    class AuthCallback extends IMiuiFaceManager.AuthenticationCallback {

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
                    Core.cancelAuthentication(MiuiFaceUnlockModule.this);
                    return;
                case BIOMETRIC_ERROR_CANCELED:
                case 123456:
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
                if (manager != null && !manager.isReleased())
                    manager.release();
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
        public void onAuthenticationSucceeded(Miuiface miuiface) {
            BiometricLoggerImpl.d(getName() + ".onAuthenticationSucceeded: " + miuiface);
            if (listener != null) {
                listener.onSuccess(tag());
            }
            if (manager != null && !manager.isReleased())
                manager.release();
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