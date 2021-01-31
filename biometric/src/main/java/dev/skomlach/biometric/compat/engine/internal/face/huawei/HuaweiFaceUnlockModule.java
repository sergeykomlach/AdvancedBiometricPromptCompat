package dev.skomlach.biometric.compat.engine.internal.face.huawei;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import com.huawei.facerecognition.FaceManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.AuthenticationHelpReason;
import dev.skomlach.biometric.compat.engine.BiometricInitListener;
import dev.skomlach.biometric.compat.engine.BiometricMethod;
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule;
import dev.skomlach.biometric.compat.engine.internal.core.Core;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate;
import dev.skomlach.biometric.compat.engine.internal.face.huawei.wrapper.HuaweiFaceManager;
import dev.skomlach.biometric.compat.engine.internal.face.huawei.wrapper.HuaweiFaceManagerFactory;
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix;
import dev.skomlach.biometric.compat.utils.CodeToString;
import dev.skomlach.biometric.compat.utils.SystemPropertiesProxy;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.misc.ExecutorHelper;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class HuaweiFaceUnlockModule extends AbstractBiometricModule {
    //EMUI 10.1.0
    private HuaweiFaceManager huaweiFaceManagerLegacy = null;
    private FaceManager huawei3DFaceManager = null;
    public HuaweiFaceUnlockModule(BiometricInitListener listener) {
        super(BiometricMethod.FACE_HUAWEI);
        try {
            huawei3DFaceManager = getFaceManager();
            BiometricLoggerImpl.d(getName() + ".huawei3DFaceManager - " + huawei3DFaceManager);
        } catch (Throwable ignore) {
            huawei3DFaceManager = null;
        }

        try {
            String versionEmui = SystemPropertiesProxy.get(getContext(), "ro.build.version.emui");
            final String emuiTag = "EmotionUI_";
            if (versionEmui.startsWith(emuiTag)) {
                versionEmui = versionEmui.substring(emuiTag.length());
            }
            BiometricLoggerImpl.d(getName() + ".EMUI version - '" + versionEmui + "'");

            //it seems like on EMUI 10.1 only system apps allowed:
            //for some reasons callback never fired
            if (!compareVersions("10.1", versionEmui))
                huaweiFaceManagerLegacy = HuaweiFaceManagerFactory.getHuaweiFaceManager(getContext());

            BiometricLoggerImpl.d(getName() + ".huaweiFaceManagerLegacy - " + huaweiFaceManagerLegacy);
        } catch (Throwable ignore) {
            huaweiFaceManagerLegacy = null;
        }

        if (listener != null) {
            listener
                    .initFinished(getBiometricMethod(), HuaweiFaceUnlockModule.this);
        }
    }

    private FaceManager getFaceManager() {
        try {
            Class<?> t = Class.forName("com.huawei.facerecognition.FaceManagerFactory");
            Method method = t.getDeclaredMethod("getFaceManager", Context.class);
            return (FaceManager) method.invoke(null, getContext());
        } catch (ClassNotFoundException var3) {
            BiometricLoggerImpl.d(getName() + ".Throw exception: ClassNotFoundException");
        } catch (NoSuchMethodException var4) {
            BiometricLoggerImpl.d(getName() + ".Throw exception: NoSuchMethodException");
        } catch (IllegalAccessException var5) {
            BiometricLoggerImpl.d(getName() + ".Throw exception: IllegalAccessException");
        } catch (InvocationTargetException var6) {
            BiometricLoggerImpl.d(getName() + ".Throw exception: InvocationTargetException");
        }
        return null;
    }

    private boolean compareVersions(String str1, String str2) {
        String[] parts1 = str1.split("\\.");
        String[] parts2 = str2.split("\\.");
        int min = Math.min(parts1.length, parts2.length);
        for (int i = 0; i < min; i++) {
            if (!TextUtils.equals(parts1[i], parts2[i]))
                return false;
        }
        return true;
    }

    @Override
    public boolean isManagerAccessible() {
        return huaweiFaceManagerLegacy != null || huawei3DFaceManager != null;
    }

    @Override
    public boolean isHardwarePresent() {
        if (huawei3DFaceManager != null) {
            try {
                if (huawei3DFaceManager.isHardwareDetected())
                    return true;
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName());
            }
        }
        if (huaweiFaceManagerLegacy != null) {
            try {
                if (huaweiFaceManagerLegacy.isHardwareDetected())
                    return true;
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName());
            }
        }

        return false;
    }

    @Override
    public boolean hasEnrolled() {
        if (huawei3DFaceManager != null) {
            try {
                if (huawei3DFaceManager.hasEnrolledTemplates())
                    return true;
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName());
            }
        }

        if (huaweiFaceManagerLegacy != null) {
            try {
                if (huaweiFaceManagerLegacy.hasEnrolledTemplates())
                    return true;
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

        if (!isHardwarePresent()) {
            listener.onFailure(AuthenticationFailureReason.NO_HARDWARE, tag());
            return;
        }
        if (!hasEnrolled()) {
            listener.onFailure(AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED, tag());
            return;
        }
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
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
            if (listener != null) {
                listener.onFailure(AuthenticationFailureReason.UNKNOWN, tag());
            }
            return;
        }
        if (huawei3DFaceManager != null && huawei3DFaceManager.isHardwareDetected() && huawei3DFaceManager.hasEnrolledTemplates()) {
            try {
                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                huawei3DFaceManager.authenticate(null, signalObject, 0, new AuthCallback3DFace(restartPredicate, cancellationSignal, listener), ExecutorHelper.INSTANCE.getHandler());
                return;
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName() + ": authenticate failed unexpectedly");
            }
        }

        if (huaweiFaceManagerLegacy != null && huaweiFaceManagerLegacy.isHardwareDetected() && huaweiFaceManagerLegacy.hasEnrolledTemplates()) {
            try {

                signalObject.setOnCancelListener(new android.os.CancellationSignal.OnCancelListener() {
                    @Override
                    public void onCancel() {
                        huaweiFaceManagerLegacy.cancel(0);
                    }
                });
                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                huaweiFaceManagerLegacy.authenticate(0, 1, new AuthCallbackLegacy(restartPredicate, cancellationSignal, listener));
                return;
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, getName() + ": authenticate failed unexpectedly");
            }
        }

        if (listener != null) {
            listener.onFailure(AuthenticationFailureReason.UNKNOWN, tag());
        }
    }

    private class AuthCallback3DFace extends FaceManager.AuthenticationCallback {

        private final RestartPredicate restartPredicate;
        private final CancellationSignal cancellationSignal;
        private final AuthenticationListener listener;

        public AuthCallback3DFace(RestartPredicate restartPredicate,
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
                    Core.cancelAuthentication(HuaweiFaceUnlockModule.this);
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

    private class AuthCallbackLegacy extends HuaweiFaceManager.AuthenticatorCallback {

        private final RestartPredicate restartPredicate;
        private final CancellationSignal cancellationSignal;
        private final AuthenticationListener listener;

        public AuthCallbackLegacy(RestartPredicate restartPredicate,
                                  CancellationSignal cancellationSignal, AuthenticationListener listener) {
            this.restartPredicate = restartPredicate;
            this.cancellationSignal = cancellationSignal;
            this.listener = listener;
        }

        @Override
        public void onAuthenticationError(int errMsgId) {
            BiometricLoggerImpl.d(getName() + ".onAuthenticationError: " + CodeToString.getErrorCode(errMsgId));
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
                    Core.cancelAuthentication(HuaweiFaceUnlockModule.this);
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
        public void onAuthenticationStatus(int helpMsgId) {
            BiometricLoggerImpl.d(getName() + ".onAuthenticationHelp: " + CodeToString.getHelpCode(helpMsgId));
            if (listener != null) {
                listener.onHelp(AuthenticationHelpReason.getByCode(helpMsgId), null);
            }
        }

        @Override
        public void onAuthenticationSucceeded() {
            BiometricLoggerImpl.d(getName() + ".onAuthenticationSucceeded: ");
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