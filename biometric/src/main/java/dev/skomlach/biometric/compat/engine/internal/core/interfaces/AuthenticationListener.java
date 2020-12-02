package dev.skomlach.biometric.compat.engine.internal.core.interfaces;

import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.AuthenticationHelpReason;

/**
 * A listener that is notified of the results of fingerprint authentication.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface AuthenticationListener {

    void onHelp(AuthenticationHelpReason helpReason, String msg);

    /**
     * Called after a fingerprint is successfully authenticated.
     *
     * @param moduleTag The {@link BiometricModule#tag()} of the module that was used for authentication.
     */
    void onSuccess(int moduleTag);

    /**
     * Called after an error or authentication failure.
     *
     * @param failureReason The general reason for the failure.
     * @param moduleTag     The {@link BiometricModule#tag()} of the module that is currently active. This is
     *                      useful to know the meaning of the error code.
     */
    void onFailure(AuthenticationFailureReason failureReason,
                   int moduleTag);
}
