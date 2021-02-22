package dev.skomlach.biometric.compat.engine.internal.core

import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate

@RestrictTo(RestrictTo.Scope.LIBRARY)
object RestartPredicatesImpl {
    /**
     * A predicate that will retry all non-fatal failures indefinitely, and timeouts a given number
     * of times.
     *
     * @param timeoutRestartCount The maximum number of times to restart after a timeout.
     */
    fun restartTimeouts(timeoutRestartCount: Int): RestartPredicate {
        return object : RestartPredicate {
            private var timeoutRestarts = 0
            override fun invoke(reason: AuthenticationFailureReason?): Boolean {
                when (reason) {
                    AuthenticationFailureReason.SENSOR_FAILED, AuthenticationFailureReason.AUTHENTICATION_FAILED -> return timeoutRestarts++ < timeoutRestartCount
                }
                return false
            }
        }
    }

    /**
     * A predicate that will retry all non-fatal failures indefinitely, and timeouts 5 times.
     */
    @kotlin.jvm.JvmStatic
    fun defaultPredicate(): RestartPredicate {
        return restartTimeouts(5)
    }

    /**
     * A predicate that will never restart after any failure.
     */
    fun neverRestart(): RestartPredicate {
        return object: RestartPredicate {
            override fun invoke(reason: AuthenticationFailureReason?): Boolean {
                return false
            }
        }
    }
}