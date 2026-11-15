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
import android.os.SystemClock
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.LegacyBiometric
import dev.skomlach.common.storage.SharedPreferenceProvider.getPreferences
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object BiometricErrorLockoutPermanentFix {
    private const val TS_PREF = "user_unlock_device"
    private const val PENDING_RESET = "pending_provider_reset"
    private val sharedPreferences: SharedPreferences =
        getPreferences("BiometricCompat_ErrorLockoutPermanentFix")
    private val lock = ReentrantLock()
    private val resetLock = ReentrantLock()

    fun setBiometricSensorPermanentlyLocked(type: BiometricType) {
        lock.withLock {
            sharedPreferences.edit()
                .putBoolean(TS_PREF + "-" + type.name, false)
                .putLong(TS_PREF + "-" + type.name + "-uptime", SystemClock.uptimeMillis())
                .apply()
        }
    }

    fun isRebootDetected(): Boolean = lock.withLock { isRebootDetectedLocked() }

    private fun isRebootDetectedLocked(): Boolean {
        val uptime = SystemClock.uptimeMillis()
        return sharedPreferences.all.any { (key, value) ->
            key.endsWith("-uptime") && value is Long && uptime < value
        }
    }

    fun resetBiometricSensorPermanentlyLocked() {
        resetLock.withLock {
            persistResetLocked()
            finishResetLocked(onlyIfDiscovered = false)
        }
    }

    // BOOT_COMPLETED may cold-start only our receiver, before the providers have been discovered.
    internal fun resetAfterBoot() = resetLock.withLock {
        persistResetLocked()
        finishResetLocked(onlyIfDiscovered = true)
    }

    internal fun resetIfRebooted(): Boolean = resetLock.withLock {
        val needsReset = lock.withLock {
            sharedPreferences.getBoolean(PENDING_RESET, false) || isRebootDetectedLocked()
        }
        if (!needsReset) return false
        persistResetLocked()
        finishResetLocked(onlyIfDiscovered = true)
        true
    }

    private fun persistResetLocked() = lock.withLock {
        check(sharedPreferences.edit().putBoolean(PENDING_RESET, true).commit()) {
            "Could not persist biometric lockout reset"
        }
    }

    private fun finishResetLocked(onlyIfDiscovered: Boolean) {
        val before = lock.withLock { sharedPreferences.all }
        if (!LegacyBiometric.resetSoftwarePermanentLockOutForDevice(onlyIfDiscovered)) return
        BiometricLockoutFix.clearDeviceLockout()
        lock.withLock {
            val current = sharedPreferences.all
            val editor = sharedPreferences.edit()
            before.forEach { (key, value) -> if (current[key] == value) editor.remove(key) }
            if (!editor.commit()) {
                // SharedPreferences can publish failed writes in-memory. Retain a retry marker
                // in this process as well as the persisted reset request.
                sharedPreferences.edit().putBoolean(PENDING_RESET, true).apply()
                error("Could not complete biometric lockout reset")
            }
        }
    }

    fun isBiometricSensorPermanentlyLocked(type: BiometricType): Boolean {
        return lock.withLock {
            !sharedPreferences.getBoolean(TS_PREF + "-" + type.name, true)
        }
    }
}
