package dev.skomlach.biometric.compat.engine.internal.core.interfaces;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.internal.core.Core;

/**
 * A reprint module handles communication with a specific fingerprint api.
 * <p/>
 * Implement this interface to add a new api to Core, then pass an instance of this interface to
 * {@link Core#registerModule(BiometricModule)}
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface BiometricModule {

    boolean isManagerAccessible();

    boolean isHardwarePresent();

    boolean isLockOut();

    boolean hasEnrolled();

    /**
     * Start a fingerprint authentication request.
     * <p/>
     * Don't call this method directly. Register an instance of this module with Core, then call
     * {@link Core#authenticate(AuthenticationListener)}
     *
     * @param cancellationSignal A signal that can cancel the authentication request.
     * @param listener           A listener that will be notified of the authentication status.
     * @param restartPredicate   If the predicate returns true, the module should ensure the sensor
     *                           is still running, and should not call any methods on the listener.
     *                           If the predicate returns false, the module should ensure the sensor
     *                           is not running before calling {@link AuthenticationListener#onFailure(AuthenticationFailureReason,
     *                           boolean, int, int)}.
     */
    void authenticate(CancellationSignal cancellationSignal, AuthenticationListener listener, RestartPredicate restartPredicate);

    /**
     * A tag uniquely identifying this class. It must be the same for all instances of each class,
     * and each class's tag must be unique among registered modules.
     */
    int tag();
}
