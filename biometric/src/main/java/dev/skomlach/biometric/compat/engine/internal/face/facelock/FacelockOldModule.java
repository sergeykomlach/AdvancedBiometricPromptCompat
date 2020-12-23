package dev.skomlach.biometric.compat.engine.internal.face.facelock;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import java.lang.ref.WeakReference;

import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.BiometricInitListener;
import dev.skomlach.biometric.compat.engine.BiometricMethod;
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule;
import dev.skomlach.biometric.compat.engine.internal.core.Core;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate;
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix;
import dev.skomlach.biometric.compat.utils.LockType;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class FacelockOldModule extends AbstractBiometricModule {

    public static final int BIOMETRIC_AUTHENTICATION_FAILED = 1001;
    private FaceLockHelper faceLockHelper = null;
    private ProxyListener facelockProxyListener = null;
    private WeakReference<View> viewWeakReference = new WeakReference<>(null);
    private BiometricInitListener listener;
    private boolean isConnected = false;

    public FacelockOldModule(BiometricInitListener initlistener) {
        super(BiometricMethod.FACELOCK);
        this.listener = initlistener;

        final FaceLockInterface faceLockInterface = new FaceLockInterface() {
            @Override
            public void onError(int code, String msg) {
                BiometricLoggerImpl.d("FaceIdModule" + ("FaceIdInterface.onError " + code + " " + msg));
                if (facelockProxyListener != null) {
                    int failureReason = BIOMETRIC_ERROR_CANCELED;
                    switch (code) {
                        case FaceLockHelper.FACELOCK_FAILED_ATTEMPT:
                            failureReason = BIOMETRIC_AUTHENTICATION_FAILED;

                            break;
                        case FaceLockHelper.FACELOCK_TIMEOUT:
                            failureReason = BIOMETRIC_ERROR_TIMEOUT;

                            break;
                        case FaceLockHelper.FACELOCK_NO_FACE_FOUND:
                            failureReason = BIOMETRIC_ERROR_UNABLE_TO_PROCESS;
                            break;
                        case FaceLockHelper.FACELOCK_NOT_SETUP:
                            failureReason = BIOMETRIC_ERROR_NO_BIOMETRICS;
                            break;
                        case FaceLockHelper.FACELOCK_CANCELED:
                            failureReason = BIOMETRIC_ERROR_CANCELED;
                            break;
                        case FaceLockHelper.FACELOCK_CANNT_START:
                        case FaceLockHelper.FACELOCK_UNABLE_TO_BIND:
                        case FaceLockHelper.FACELOCK_API_NOT_FOUND:
                            failureReason = BIOMETRIC_ERROR_HW_UNAVAILABLE;
                            break;
                    }
                    facelockProxyListener.onAuthenticationError(failureReason, msg);
                }
            }

            @Override
            public void onAuthorized() {
                BiometricLoggerImpl.d("FaceIdModule" + "FaceIdInterface.onAuthorized");
                if (facelockProxyListener != null) {
                    facelockProxyListener.onAuthenticationSucceeded(null);
                }
            }

            @Override
            public void onConnected() {
                BiometricLoggerImpl.d("FaceIdModule" + "FaceIdInterface.onConnected");
                if (facelockProxyListener != null) {
                    facelockProxyListener.onAuthenticationAcquired(0);
                }

                if (listener != null) {
                    isConnected = true;
                    listener.initFinished(BiometricMethod.FACELOCK, FacelockOldModule.this);
                    listener = null;
                    faceLockHelper.stopFaceLock();
                } else {
                    BiometricLoggerImpl.d("FaceIdModule" + ("authorize: " + viewWeakReference.get()));
                    faceLockHelper.startFaceLockWithUi(viewWeakReference.get());
                }
            }

            @Override
            public void onDisconnected() {
                BiometricLoggerImpl.d("FaceIdModule" + "FaceIdInterface.onDisconnected");
                if (facelockProxyListener != null) {
                    facelockProxyListener.onAuthenticationError(BIOMETRIC_ERROR_CANCELED,
                            FaceLockHelper.getMessage(BIOMETRIC_ERROR_CANCELED));
                }
                if (listener != null) {
                    listener.initFinished(BiometricMethod.FACELOCK, FacelockOldModule.this);
                    listener = null;
                    faceLockHelper.stopFaceLock();
                }
            }
        };

        faceLockHelper = new FaceLockHelper(getContext(), faceLockInterface);
        if (!isHardwarePresent()) {
            if (listener != null) {
                listener.initFinished(BiometricMethod.FACELOCK, FacelockOldModule.this);
                listener = null;
            }
            return;
        } else {
            faceLockHelper.initFacelock();
        }
    }

    public void stopAuth() {
        faceLockHelper.stopFaceLock();
        faceLockHelper.destroy();
    }

    @Override
    public boolean isManagerAccessible() {
        return isConnected;
    }

    @Override
    public boolean isHardwarePresent() {
        // Retrieve all services that can match the given intent
        if (!faceLockHelper.faceUnlockAvailable())
            return false;

        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            return false;
        }

        DevicePolicyManager dpm = (DevicePolicyManager) getContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm.getCameraDisabled(null)) {
            return false;
        }

        return hasEnrolled();
    }

    @Override
    public boolean hasEnrolled() throws SecurityException {
        return LockType.isBiometricWeakEnabled(getContext());
    }

    @Override
    public void authenticate(final CancellationSignal cancellationSignal,
                             final AuthenticationListener listener,
                             final RestartPredicate restartPredicate) throws SecurityException {

        try {
            BiometricLoggerImpl.d("FaceIdModule" + "Facelock call authorize");
            authorize(new ProxyListener(restartPredicate, cancellationSignal, listener));
            return;
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e, "BiometricGenericModule: authenticate failed unexpectedly");
        }

        if (listener != null) {
            listener.onFailure(AuthenticationFailureReason.UNKNOWN,
                    tag());
        }
    }

    public void setCallerView(View targetView) {
        BiometricLoggerImpl.d("FaceIdModule" + ("setCallerView: " + targetView));
        viewWeakReference = new WeakReference<>(targetView);
    }

    private void authorize(ProxyListener proxyListener) {
        this.facelockProxyListener = proxyListener;
        faceLockHelper.initFacelock();
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public class ProxyListener {

        private final RestartPredicate restartPredicate;
        private final CancellationSignal cancellationSignal;
        private final AuthenticationListener listener;

        public ProxyListener(RestartPredicate restartPredicate,
                             CancellationSignal cancellationSignal, AuthenticationListener listener) {
            this.restartPredicate = restartPredicate;
            this.cancellationSignal = cancellationSignal;
            this.listener = listener;
        }

        public Void onAuthenticationError(int errMsgId, CharSequence errString) {
            AuthenticationFailureReason failureReason = AuthenticationFailureReason.UNKNOWN;
            switch (errMsgId) {
                case BIOMETRIC_ERROR_NO_BIOMETRICS:
                    failureReason = AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED;
                    break;
                case BIOMETRIC_AUTHENTICATION_FAILED:
                    failureReason = AuthenticationFailureReason.AUTHENTICATION_FAILED;
                    break;
                case BIOMETRIC_ERROR_HW_NOT_PRESENT:
                    failureReason = AuthenticationFailureReason.NO_HARDWARE;
                    break;
                case BIOMETRIC_ERROR_HW_UNAVAILABLE:
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE;
                    break;
                case BIOMETRIC_ERROR_LOCKOUT_PERMANENT:
                    BiometricErrorLockoutPermanentFix.INSTANCE.setBiometricSensorPermanentlyLocked(getType());
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
                    Core.cancelAuthentication(FacelockOldModule.this);
                    return null;
                case BIOMETRIC_ERROR_CANCELED:
                    // Don't send a cancelled message.
                    return null;
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
            return null;
        }

        public Void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            return null;
        }

        public Void onAuthenticationSucceeded(Object result) {
            if (listener != null) {
                listener.onSuccess(tag());
            }
            return null;
        }

        public Void onAuthenticationAcquired(int acquireInfo) {
            BiometricLoggerImpl.d("FaceIdModule" + ("FaceIdInterface.ProxyListener " + acquireInfo));
            return null;
        }

        public Void onAuthenticationFailed() {
            if (listener != null) {
                listener.onFailure(AuthenticationFailureReason.AUTHENTICATION_FAILED,
                        tag());
            }
            return null;
        }
    }
}