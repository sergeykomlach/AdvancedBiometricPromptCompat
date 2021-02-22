package dev.skomlach.biometric.compat.engine.internal.core.interfaces

import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface RestartPredicate {
    /**
     * Return true if the authentication should be restarted after the given non-fatal failure.
     *
     * @param reason The reason for this failure.
     */
    operator fun invoke(reason: AuthenticationFailureReason?): Boolean
}