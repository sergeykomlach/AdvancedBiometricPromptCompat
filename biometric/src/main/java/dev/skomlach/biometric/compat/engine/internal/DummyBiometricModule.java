package dev.skomlach.biometric.compat.engine.internal;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.BiometricInitListener;
import dev.skomlach.biometric.compat.engine.BiometricMethod;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.misc.ExecutorHelper;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class DummyBiometricModule extends AbstractBiometricModule {

    private BiometricInitListener listener = null;

    public DummyBiometricModule(BiometricInitListener listener) {
        super(BiometricMethod.DUMMY_BIOMETRIC.getId());
        this.listener = listener;
        if (listener != null) {
            listener
                    .initFinished(BiometricMethod.DUMMY_BIOMETRIC, DummyBiometricModule.this);
        }
    }

    @Override
    public boolean isManagerAccessible() {
        return false;//BuildConfig.DEBUG;
    }

    @Override
    public boolean isHardwarePresent() {
        return true;
    }

    @Override
    public boolean hasEnrolled() {
        return true;
    }

    @Override
    public void authenticate(final CancellationSignal cancellationSignal,
                             final AuthenticationListener listener,
                             final RestartPredicate restartPredicate) throws SecurityException {

        for (BiometricMethod method : BiometricMethod.values()) {
            if (method.getId() == tag()) {
                BiometricLoggerImpl.d("DummyBiometricModule.authenticate - " + method.toString());
            }
        }
        ExecutorHelper.INSTANCE.getHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onFailure(AuthenticationFailureReason.AUTHENTICATION_FAILED, BiometricMethod.DUMMY_BIOMETRIC.getId());
                }
            }
        }, 2500);
    }
}