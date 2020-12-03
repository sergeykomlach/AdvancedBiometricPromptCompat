package dev.skomlach.biometric.compat.engine;

import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

import dev.skomlach.biometric.compat.BiometricType;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface BiometricAuthenticationListener {

    //user identity confirmed in module
    @WorkerThread
    void onSuccess(BiometricType module);

    @WorkerThread
    void onHelp(AuthenticationHelpReason helpReason, String msg);

    //failure happens in module
    @WorkerThread
    void onFailure(AuthenticationFailureReason failureReason,
                   BiometricType module);
}
