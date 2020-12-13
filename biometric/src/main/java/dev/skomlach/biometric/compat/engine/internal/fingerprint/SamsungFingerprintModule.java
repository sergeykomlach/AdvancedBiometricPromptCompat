package dev.skomlach.biometric.compat.engine.internal.fingerprint;

import android.content.Context;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import com.samsung.android.sdk.pass.Spass;
import com.samsung.android.sdk.pass.SpassFingerprint;

import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.BiometricInitListener;
import dev.skomlach.biometric.compat.engine.BiometricMethod;
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

import static com.samsung.android.sdk.pass.SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class SamsungFingerprintModule extends AbstractBiometricModule {

    private Spass mSpass = null;
    private SpassFingerprint mSpassFingerprint = null;

    public SamsungFingerprintModule(BiometricInitListener listener) {
        super(BiometricMethod.FINGERPRINT_SAMSUNG.getId());

        try {
            mSpass = new Spass();

            mSpass.initialize(getContext());
            if (!mSpass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT)) {
                throw new RuntimeException("No hardware");
            }
            mSpassFingerprint = new SpassFingerprint(getContext());
        } catch (Throwable ignore) {
            mSpass = null;
            mSpassFingerprint = null;
        }

        if (listener != null) {
            listener
                    .initFinished(BiometricMethod.FINGERPRINT_SAMSUNG, SamsungFingerprintModule.this);
        }
    }

    @Override
    public boolean isManagerAccessible() {
        return mSpass != null && mSpassFingerprint != null;
    }

    @Override
    public boolean isHardwarePresent() {

        if (mSpass != null) {
            try {
                if (mSpass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT)) {
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

        if (mSpassFingerprint != null) {
            try {
                if (mSpassFingerprint.hasRegisteredFinger()) {
                    return true;
                }
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
                BiometricLoggerImpl.d("SamsungBiometricModule.authenticate - " + method.toString());
            }
        }


        if (mSpassFingerprint != null) {
            try {
                cancelFingerprintRequest();

                mSpassFingerprint.startIdentify(new SpassFingerprint.IdentifyListener() {
                    @Override
                    public void onFinished(int status) {
                        switch (status) {
                            case SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS:
                            case STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS:
                                if (listener != null) {
                                    listener.onSuccess(tag());
                                }
                                return;
                            case SpassFingerprint.STATUS_QUALITY_FAILED:
                            case SpassFingerprint.STATUS_SENSOR_FAILED:
                                fail(AuthenticationFailureReason.SENSOR_FAILED);
                                break;
                            case SpassFingerprint.STATUS_AUTHENTIFICATION_FAILED:
                                fail(AuthenticationFailureReason.AUTHENTICATION_FAILED);
                                break;
                            case SpassFingerprint.STATUS_TIMEOUT_FAILED:
                                fail(AuthenticationFailureReason.TIMEOUT);
                                break;
                            default:
                                fail(AuthenticationFailureReason.UNKNOWN);
                                break;
                            case SpassFingerprint.STATUS_USER_CANCELLED:
                                // Don't send a cancelled message.
                                break;
                        }
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
                        }
                    }

                    @Override
                    public void onReady() {
                    }

                    @Override
                    public void onStarted() {
                    }

                    @Override
                    public void onCompleted() {

                    }
                });

                cancellationSignal.setOnCancelListener(() -> cancelFingerprintRequest());

                return;
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, "SamsungBiometricModule: authenticate failed unexpectedly");
            }
        }

        if (listener != null) {
            listener.onFailure(AuthenticationFailureReason.UNKNOWN,
                    tag());
        }
        return;
    }

    private void cancelFingerprintRequest() {
        try {
            if (mSpassFingerprint != null) {
                mSpassFingerprint.cancelIdentify();
            }
        } catch (Throwable t) {
            // There's no way to query if there's an active identify request,
            // so just try to cancel and ignore any exceptions.
        }
    }

    public boolean openSettings(Context context) {
        try {
            mSpassFingerprint.registerFinger(context, new SpassFingerprint.RegisterListener() {
                @Override
                public void onFinished() {

                }
            });
            return true;
        } catch (Exception e) {
            BiometricLoggerImpl.e(e);
            return false;
        }
    }
}