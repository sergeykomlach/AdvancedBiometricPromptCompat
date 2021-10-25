/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.biometric.compat.engine.core

import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate

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
        return object : RestartPredicate {
            override fun invoke(reason: AuthenticationFailureReason?): Boolean {
                return false
            }
        }
    }
}