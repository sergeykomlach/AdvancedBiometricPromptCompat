package dev.skomlach.biometric.compat.engine.internal.face.oneplus;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.BiometricInitListener;
import dev.skomlach.biometric.compat.engine.BiometricMethod;
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule;
import dev.skomlach.biometric.compat.engine.internal.core.Core;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate;
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix;
import dev.skomlach.biometric.compat.utils.SettingsHelper;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class OnePlusFaceUnlockModule extends AbstractBiometricModule {
    private static final String ONEPLUS_AUTO_FACE_UNLOCK_ENABLE = "oneplus_auto_face_unlock_enable";
    private static final String ONEPLUS_FACE_UNLOCK_ENABLE = "oneplus_face_unlock_enable";
    public static final int BIOMETRIC_AUTHENTICATION_FAILED = 1001;
    private OnePlusFaceUnlockHelper onePlusFaceUnlockHelper = null;
    private ProxyListener facelockProxyListener = null;
    private BiometricInitListener listener;
    private boolean isConnected = false;

    public OnePlusFaceUnlockModule(BiometricInitListener initlistener) {
        super(BiometricMethod.FACE_ONEPLUS);
        this.listener = initlistener;

        final OnePlusFaceUnlockInterface onePlusFaceUnlockInterface = new OnePlusFaceUnlockInterface() {
            @Override
            public void onError(int code, String msg) {
                BiometricLoggerImpl.d(getName() + ".OnePlusFaceUnlockInterface.onError " + code + " " + msg);
                if (facelockProxyListener != null) {
                    int failureReason = BIOMETRIC_ERROR_CANCELED;
                    switch (code) {
                        case OnePlusFaceUnlockHelper.FACEUNLOCK_FAILED_ATTEMPT:
                            failureReason = BIOMETRIC_AUTHENTICATION_FAILED;
                            break;
                        case OnePlusFaceUnlockHelper.FACEUNLOCK_TIMEOUT:
                            failureReason = BIOMETRIC_ERROR_TIMEOUT;
                            break;
                        default:
                            failureReason = BIOMETRIC_ERROR_HW_UNAVAILABLE;
                            break;
                            
//                        case OnePlusFaceUnlockHelper.FACELOCK_NO_FACE_FOUND:
//                            failureReason = BIOMETRIC_ERROR_UNABLE_TO_PROCESS;
//                            break;
//                        case OnePlusFaceUnlockHelper.FACELOCK_NOT_SETUP:
//                            failureReason = BIOMETRIC_ERROR_NO_BIOMETRICS;
//                            break;
//                        case OnePlusFaceUnlockHelper.FACELOCK_CANCELED:
//                            failureReason = BIOMETRIC_ERROR_CANCELED;
//                            break;
//                        case OnePlusFaceUnlockHelper.FACELOCK_CANNT_START:
//                        case OnePlusFaceUnlockHelper.FACELOCK_UNABLE_TO_BIND:
//                        case OnePlusFaceUnlockHelper.FACELOCK_API_NOT_FOUND:
//                            failureReason = BIOMETRIC_ERROR_HW_UNAVAILABLE;
//                            break;
                    }
                    facelockProxyListener.onAuthenticationError(failureReason, msg);
                }
            }

            @Override
            public void onAuthorized() {
                BiometricLoggerImpl.d(getName() + ".OnePlusFaceUnlockInterface.onAuthorized");
                if (facelockProxyListener != null) {
                    facelockProxyListener.onAuthenticationSucceeded(null);
                }
            }

            @Override
            public void onConnected() {
                BiometricLoggerImpl.d(getName() + ".OnePlusFaceUnlockInterface.onConnected");
                if (facelockProxyListener != null) {
                    facelockProxyListener.onAuthenticationAcquired(0);
                }

                if (listener != null) {
                    isConnected = true;
                    listener.initFinished(getBiometricMethod(), OnePlusFaceUnlockModule.this);
                    listener = null;
                    onePlusFaceUnlockHelper.stopFaceLock();
                } else {
                    BiometricLoggerImpl.d(getName() + ".authorize:");
                    onePlusFaceUnlockHelper.startFaceLock();
                }
            }

            @Override
            public void onDisconnected() {
                BiometricLoggerImpl.d(getName() + ".OnePlusFaceUnlockInterface.onDisconnected");
                if (facelockProxyListener != null) {
                    facelockProxyListener.onAuthenticationError(BIOMETRIC_ERROR_CANCELED,
                            OnePlusFaceUnlockHelper.getMessage(BIOMETRIC_ERROR_CANCELED));
                }
                if (listener != null) {
                    listener.initFinished(getBiometricMethod(), OnePlusFaceUnlockModule.this);
                    listener = null;
                    onePlusFaceUnlockHelper.stopFaceLock();
                }
            }
        };

        onePlusFaceUnlockHelper = new OnePlusFaceUnlockHelper(getContext(), onePlusFaceUnlockInterface);
        if (!isHardwarePresent()) {
            if (listener != null) {
                listener.initFinished(getBiometricMethod(), OnePlusFaceUnlockModule.this);
                listener = null;
            }
            return;
        } else {
            onePlusFaceUnlockHelper.initFacelock();
        }
    }

    public void stopAuth() {
        onePlusFaceUnlockHelper.stopFaceLock();
        onePlusFaceUnlockHelper.destroy();
    }

    @Override
    public boolean isManagerAccessible() {
        return isConnected;
    }

    @Override
    public boolean isHardwarePresent() {
        // Retrieve all services that can match the given intent
        if (!onePlusFaceUnlockHelper.faceUnlockAvailable())
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
        return SettingsHelper.getInt(getContext(), ONEPLUS_FACE_UNLOCK_ENABLE, 0) == 1
//                ||SettingsHelper.getInt(getContext(), ONEPLUS_AUTO_FACE_UNLOCK_ENABLE, 0) == 1
                ;
    }

    @Override
    public void authenticate(final CancellationSignal cancellationSignal,
                             final AuthenticationListener listener,
                             final RestartPredicate restartPredicate) throws SecurityException {

        try {
            BiometricLoggerImpl.d(getName() + ".Facelock call authorize");
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

    private void authorize(ProxyListener proxyListener) {
        this.facelockProxyListener = proxyListener;
        onePlusFaceUnlockHelper.initFacelock();
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
                    Core.cancelAuthentication(OnePlusFaceUnlockModule.this);
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
            BiometricLoggerImpl.d(getName() + ".OnePlusFaceUnlockInterface.ProxyListener " + acquireInfo);
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