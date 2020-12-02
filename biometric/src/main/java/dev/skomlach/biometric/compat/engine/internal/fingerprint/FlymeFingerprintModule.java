package dev.skomlach.biometric.compat.engine.internal.fingerprint;

import android.os.IBinder;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import com.fingerprints.service.FingerprintManager;
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.BiometricInitListener;
import dev.skomlach.biometric.compat.engine.BiometricMethod;
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

import java.lang.reflect.Method;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class FlymeFingerprintModule extends AbstractBiometricModule {

    private FingerprintManager mFingerprintServiceFingerprintManager = null;
    private boolean isFingerprintServiceSupported = false;

    public FlymeFingerprintModule(BiometricInitListener listener) {
        super(BiometricMethod.FINGERPRINT_FLYME.getId());

        try {
            Class<?> servicemanager = Class.forName("android.os.ServiceManager");
            Method getService = servicemanager.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "fingerprints_service");
            if (binder != null) {
                mFingerprintServiceFingerprintManager = FingerprintManager.open();
                isFingerprintServiceSupported = (mFingerprintServiceFingerprintManager != null) && mFingerprintServiceFingerprintManager.isSurpport();
            }
        } catch (Throwable ignore) {
        } finally {
            cancelFingerprintServiceFingerprintRequest();
        }
        if (listener != null) {
            listener
                    .initFinished(BiometricMethod.FINGERPRINT_FLYME, FlymeFingerprintModule.this);
        }
    }

    @Override
    public boolean isManagerAccessible() {
        return isFingerprintServiceSupported;
    }

    @Override
    public boolean isHardwarePresent() {
        if (isFingerprintServiceSupported) {
            try {
                mFingerprintServiceFingerprintManager = FingerprintManager.open();
                return mFingerprintServiceFingerprintManager
                        .isFingerEnable();
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e);
            } finally {
                cancelFingerprintServiceFingerprintRequest();
            }
        }

        return false;
    }

    @Override
    public boolean hasEnrolled() {

        if (isFingerprintServiceSupported) {
            try {
                mFingerprintServiceFingerprintManager = FingerprintManager.open();

                int[] fingerprintIds = mFingerprintServiceFingerprintManager.getIds();

                return (fingerprintIds != null && fingerprintIds.length > 0);
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e);
            } finally {
                cancelFingerprintServiceFingerprintRequest();
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
                BiometricLoggerImpl.d("FlymeBiometricModule.authenticate - " + method.toString());
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

        if (isFingerprintServiceSupported) {
            try {
                cancelFingerprintServiceFingerprintRequest();

                mFingerprintServiceFingerprintManager = FingerprintManager.open();
                mFingerprintServiceFingerprintManager
                        .startIdentify(new FingerprintManager.IdentifyCallback() {
                            @Override
                            public void onIdentified(int i, boolean b) {
                                if (listener != null) {
                                    listener.onSuccess(tag());
                                }
                                cancelFingerprintServiceFingerprintRequest();
                            }

                            @Override
                            public void onNoMatch() {
                                fail(AuthenticationFailureReason.AUTHENTICATION_FAILED);
                            }

                            private void fail(AuthenticationFailureReason failureReason) {
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
                                    cancelFingerprintServiceFingerprintRequest();
                                }
                            }
                        }, mFingerprintServiceFingerprintManager.getIds());
                cancellationSignal.setOnCancelListener(() -> cancelFingerprintServiceFingerprintRequest());
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, "FlymeBiometricModule: authenticate failed unexpectedly");
            }
        }

        if (listener != null) {
            listener.onFailure(AuthenticationFailureReason.UNKNOWN, tag());
        }
        return;
    }

    private void cancelFingerprintServiceFingerprintRequest() {
        try {
            if (mFingerprintServiceFingerprintManager != null) {
                mFingerprintServiceFingerprintManager.abort();
                mFingerprintServiceFingerprintManager.release();
                mFingerprintServiceFingerprintManager = null;
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
            // There's no way to query if there's an active identify request,
            // so just try to cancel and ignore any exceptions.
        }
    }
}