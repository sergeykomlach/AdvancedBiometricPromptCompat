/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.compat.utils

import android.content.SharedPreferences
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.storage.SharedPreferenceProvider
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

object BiometricLockoutFix {
    //LockOut behavior emulated, because for example Meizu API allow to enroll fingerprint unlimited times
    private const val TS_PREF = "timestamp_"
    private val timeout = TimeUnit.SECONDS.toMillis(31)
    private val preferences: SharedPreferences =
        SharedPreferenceProvider.getPreferences("BiometricCompat_Storage")
    private val lock = ReentrantLock()
    fun reset() {
        try {
            lock.runCatching { this.lock() }
            preferences.edit().clear().apply()
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }

    fun lockout(biometricType: BiometricType) {
        try {
            lock.runCatching { this.lock() }
            BiometricLoggerImpl.d("BiometricLockoutFix.setLockout for " + biometricType.name)
            preferences.edit().putLong(
                TS_PREF + "-" + biometricType.name,
                System.currentTimeMillis()
            ).apply()
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }

    }


    fun isLockOut(biometricType: BiometricType): Boolean {
        try {
            lock.runCatching { this.lock() }
            val ts = preferences.getLong(TS_PREF + "-" + biometricType.name, 0)
            return if (ts > 0) {
                if (System.currentTimeMillis() - ts >= timeout) {
                    preferences.edit()
                        .putLong(TS_PREF + "-" + biometricType.name, 0).apply()
                    BiometricLoggerImpl.d("BiometricLockoutFix.lockout is FALSE(1) for " + biometricType.name)
                    false
                } else {
                    BiometricLoggerImpl.d("BiometricLockoutFix.lockout is TRUE for " + biometricType.name)
                    true
                }
            } else {
                BiometricLoggerImpl.d("BiometricLockoutFix.lockout is FALSE(2) for " + biometricType.name)
                false
            }
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }

}