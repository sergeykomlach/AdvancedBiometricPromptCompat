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

import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.utils.BiometricLockoutFix
import dev.skomlach.common.contextprovider.AndroidContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

abstract class AbstractBiometricModule(val biometricMethod: BiometricMethod) : BiometricModule {
    companion object {
        var DEBUG_MANAGERS = false
    }

    private var firstTimeout: Long? = null
    private val tag: Int = biometricMethod.id
    val name: String
        get() = javaClass.simpleName
    val context = AndroidContext.appContext
    var bundle: Bundle? = null
    override val isUserAuthCanByUsedWithCrypto: Boolean
        get() = true
    protected val authCallTimestamp = AtomicLong(0)
    fun getUserId(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                UserHandle::class.java.methods.filter { it.name == "myUserId" }[0].invoke(null) as Int
            } else {
                0
            }
        } catch (ignore: Throwable) {
            0
        }
    }
    fun lockout() {
        if (!isLockOut) {
            BiometricLockoutFix.lockout(biometricMethod.biometricType)
        }
    }

    override fun tag(): Int {
        return tag
    }

    override val isLockOut: Boolean
        get() {
            return BiometricLockoutFix.isLockOut(biometricMethod.biometricType)
        }

    fun restartCauseTimeout(reason: AuthenticationFailureReason?): Boolean {
        if (reason == AuthenticationFailureReason.TIMEOUT) {
            val current = System.currentTimeMillis()
            return if (firstTimeout == null) {
                firstTimeout = current
                true
            } else {
                val safeTimeout =
                    current - (firstTimeout ?: return false) <= TimeUnit.SECONDS.toMillis(30)
                if (!safeTimeout) {
                    firstTimeout = null
                }
                safeTimeout
            }
        }

        firstTimeout = null
        return false
    }
}