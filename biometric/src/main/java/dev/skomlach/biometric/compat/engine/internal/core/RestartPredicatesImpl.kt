package dev.skomlach.biometric.compat.engine.internal.core;

import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class RestartPredicatesImpl {
    /**
     * A predicate that will retry all non-fatal failures indefinitely, and timeouts a given number
     * of times.
     *
     * @param timeoutRestartCount The maximum number of times to restart after a timeout.
     */
    public static RestartPredicate restartTimeouts(final int timeoutRestartCount) {
        return new RestartPredicate() {
            private int timeoutRestarts = 0;

            @Override
            public boolean invoke(AuthenticationFailureReason reason) {
                switch (reason) {
                    case SENSOR_FAILED:
                    case AUTHENTICATION_FAILED:
                        return timeoutRestarts++ < timeoutRestartCount;
                }

                return false;
            }
        };
    }

    /**
     * A predicate that will retry all non-fatal failures indefinitely, and timeouts 5 times.
     */
    public static RestartPredicate defaultPredicate() {
        return restartTimeouts(5);
    }

    /**
     * A predicate that will never restart after any failure.
     */
    public static RestartPredicate neverRestart() {
        return reason -> false;
    }
}
