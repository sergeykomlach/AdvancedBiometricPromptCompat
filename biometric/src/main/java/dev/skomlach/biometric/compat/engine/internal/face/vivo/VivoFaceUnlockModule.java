package dev.skomlach.biometric.compat.engine.internal.face.vivo;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;
import com.vivo.framework.facedetect.FaceDetectManager;
import java.util.Collections;
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.BiometricInitListener;
import dev.skomlach.biometric.compat.engine.BiometricMethod;
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.misc.ExecutorHelper;
import me.weishu.reflection.Reflection;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class VivoFaceUnlockModule extends AbstractBiometricModule {
    private FaceDetectManager manager = null;

    @SuppressLint("WrongConstant")
    public VivoFaceUnlockModule(BiometricInitListener listener) {
        super(BiometricMethod.FACE_VIVO);
        Reflection.unseal(getContext(), Collections.singletonList("com.vivo.framework.facedetect"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                manager = getContext().getSystemService(FaceDetectManager.class);
            } catch (Throwable ignore) {
                manager = null;
            }
        } else {
            try {
                manager = (FaceDetectManager) getContext().getSystemService("face_detect_service");
            } catch (Throwable ignore) {
                manager = null;
            }
        }
        if (manager == null)
            try {
                manager = FaceDetectManager.getInstance();
            } catch (Throwable ignore) {
                manager = null;
            }
        if (listener != null) {
            listener
                    .initFinished(getBiometricMethod(), VivoFaceUnlockModule.this);
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
                return manager.isFaceUnlockEnable();
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
                return manager.isFaceUnlockEnable() && manager.hasFaceID();
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

                final FaceDetectManager.FaceAuthenticationCallback callback =
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

                // Occasionally, an NPE will bubble up out of SemBioSomeManager.authenticate
                manager.startFaceUnlock(callback);
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

    class AuthCallback extends FaceDetectManager.FaceAuthenticationCallback {

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
        public void onFaceAuthenticationResult(int errorCode, int retry_times) {
            BiometricLoggerImpl.d(getName() + ".onFaceAuthenticationResult: " + errorCode + "-" + retry_times);

            if (errorCode == FaceDetectManager.FACE_DETECT_SUCEESS) {
                if (listener != null) {
                    listener.onSuccess(tag());
                }
                return;
            }
            AuthenticationFailureReason failureReason = AuthenticationFailureReason.UNKNOWN;
            switch (errorCode) {
                case FaceDetectManager.FACE_DETECT_NO_FACE:
                    failureReason = AuthenticationFailureReason.SENSOR_FAILED;
                    break;
                case FaceDetectManager.FACE_DETECT_BUSY:
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE;
                    break;
                case FaceDetectManager.FACE_DETECT_FAILED:
                    failureReason = AuthenticationFailureReason.AUTHENTICATION_FAILED;
                    break;
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
    }
}