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

package dev.skomlach.biometric.compat.engine.internal

import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.common.misc.ExecutorHelper

class DummyBiometricModule(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.DUMMY_BIOMETRIC) {

    init {
        listener?.initFinished(biometricMethod, this@DummyBiometricModule)
    }

    override fun getManagers(): Set<Any> {
        return emptySet()
    }

    //BuildConfig.DEBUG;
    override val isManagerAccessible: Boolean
        get() = false //BuildConfig.DEBUG;
    override val isHardwarePresent: Boolean
        get() = true

    override fun hasEnrolled(): Boolean {
        return true
    }

    @Throws(SecurityException::class)
    override fun authenticate(
        biometricCryptoObject: BiometricCryptoObject?,
        cancellationSignal: CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        d("$name.authenticate - $biometricMethod; Crypto=$biometricCryptoObject")
        ExecutorHelper.postDelayed({
            listener?.onFailure(
                AuthenticationFailureReason.AUTHENTICATION_FAILED,
                biometricMethod.id
            )
        }, 2500)
    }
}