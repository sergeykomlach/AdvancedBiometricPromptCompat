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

package dev.skomlach.biometric.compat.custom

import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.Mac

abstract class AbstractSoftwareBiometricManager {
    companion object {
        const val CUSTOM_BIOMETRIC_ACQUIRED_GOOD = 0
        const val CUSTOM_BIOMETRIC_ACQUIRED_IMAGER_DIRTY = 3
        const val CUSTOM_BIOMETRIC_ACQUIRED_INSUFFICIENT = 2
        const val CUSTOM_BIOMETRIC_ACQUIRED_PARTIAL = 1
        const val CUSTOM_BIOMETRIC_ACQUIRED_TOO_FAST = 5
        const val CUSTOM_BIOMETRIC_ACQUIRED_TOO_SLOW = 4
        const val CUSTOM_BIOMETRIC_ERROR_CANCELED = 5
        const val CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT = 12
        const val CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE = 1
        const val CUSTOM_BIOMETRIC_ERROR_LOCKOUT = 7
        const val CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT = 9
        const val CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC = 11
        const val CUSTOM_BIOMETRIC_ERROR_NO_PERMISSIONS = 13
        const val CUSTOM_BIOMETRIC_ERROR_NO_SPACE = 4
        const val CUSTOM_BIOMETRIC_ERROR_TIMEOUT = 3
        const val CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS = 2
        const val CUSTOM_BIOMETRIC_ERROR_USER_CANCELED = 10
        const val CUSTOM_BIOMETRIC_ERROR_VENDOR = 8

        const val PRIORITY_BELOW_SYSTEM_HARDWARE =
            BiometricModule.PRIORITY_BELOW_SYSTEM_HARDWARE
        const val PRIORITY_SYSTEM_HARDWARE =
            BiometricModule.PRIORITY_SYSTEM_HARDWARE
        const val PRIORITY_ABOVE_SYSTEM_HARDWARE =
            BiometricModule.PRIORITY_ABOVE_SYSTEM_HARDWARE

        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_END_TIMESTAMP = "lockout_end_timestamp"
        private const val KEY_PERMANENT_LOCKOUT_COUNT = "permanent_lockout_count"
    }

    open val priority: Int = PRIORITY_BELOW_SYSTEM_HARDWARE

    protected data class LockoutPolicy(
        val maxFailedAttemptsBeforeLockout: Int,
        val maxTemporaryLockoutsBeforePermanent: Int,
        val lockoutDurationMs: Long
    )

    abstract fun getTimeoutMessage(): CharSequence?
    abstract fun resetLockOut()
    abstract fun resetPermanentLockOut()
    abstract fun getPermissions(): List<String>

    open fun prepareForAuthentication(
        callback: PreparationCallback
    ) {
        callback.onPrepared()
    }

    open fun getLockoutError(): Int? = null

    open fun isLockedOut(): Boolean = getLockoutError() != null

    protected fun resetTemporaryLockoutState(prefs: SharedPreferences) {
        prefs.edit {
            remove(KEY_LOCKOUT_END_TIMESTAMP)
            remove(KEY_FAILED_ATTEMPTS)
        }
    }

    protected fun resetPermanentLockoutState(prefs: SharedPreferences) {
        prefs.edit {
            remove(KEY_FAILED_ATTEMPTS)
            remove(KEY_LOCKOUT_END_TIMESTAMP)
            remove(KEY_PERMANENT_LOCKOUT_COUNT)
        }
    }

    protected fun getStoredLockoutError(
        prefs: SharedPreferences,
        policy: LockoutPolicy
    ): Int? {
        val permanentLockoutCount = prefs.getInt(KEY_PERMANENT_LOCKOUT_COUNT, 0)
        if (permanentLockoutCount >= policy.maxTemporaryLockoutsBeforePermanent) {
            return CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        }

        val lockoutEndTime = prefs.getLong(KEY_LOCKOUT_END_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()
        if (lockoutEndTime > currentTime) {
            return CUSTOM_BIOMETRIC_ERROR_LOCKOUT
        } else if (lockoutEndTime > 0) {
            resetTemporaryLockoutState(prefs)
        }
        return null
    }

    protected fun recordFailedAttempt(
        prefs: SharedPreferences,
        policy: LockoutPolicy
    ) {
        var failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        var permanentLockoutCount = prefs.getInt(KEY_PERMANENT_LOCKOUT_COUNT, 0)

        prefs.edit {
            if (failedAttempts >= policy.maxFailedAttemptsBeforeLockout) {
                permanentLockoutCount++
                failedAttempts = 0
                if (permanentLockoutCount < policy.maxTemporaryLockoutsBeforePermanent) {
                    putLong(
                        KEY_LOCKOUT_END_TIMESTAMP,
                        System.currentTimeMillis() + policy.lockoutDurationMs
                    )
                }
                putInt(KEY_PERMANENT_LOCKOUT_COUNT, permanentLockoutCount)
            }
            putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
        }
    }

    abstract val biometricType: BiometricType
    abstract fun isHardwareDetected(): Boolean
    abstract fun hasEnrolledBiometric(): Boolean

    abstract fun getManagers(): Set<Any>

    abstract fun remove(extra: Bundle?)
    abstract fun getEnrollBundle(name: String? = null): Bundle

    abstract fun getEnrolls(): Collection<String>

    abstract fun authenticate(
        crypto: CryptoObject?,
        flags: Int,
        cancel: CancellationSignal?,
        callback: AuthenticationCallback?,
        handler: Handler?,
        extra: Bundle?
    )

    abstract class AuthenticationCallback {
        open fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {}
        open fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {}
        open fun onAuthenticationSucceeded(result: AuthenticationResult?) {}
        open fun onAuthenticationFailed() {}
        open fun onAuthenticationCancelled() {}
    }

    abstract class PreparationCallback {
        open fun onPrepared() {}
        open fun onPreparationError(errMsgId: Int, errString: CharSequence?) {}
        open fun onPreparationCanceled() {}
    }

    class AuthenticationResult(val cryptoObject: CryptoObject?)

    class CryptoObject {
        val signature: Signature?
        val cipher: Cipher?
        val mac: Mac?

        constructor(signature: Signature?) {
            this.signature = signature
            cipher = null
            mac = null
        }

        constructor(cipher: Cipher?) {
            this.cipher = cipher
            signature = null
            mac = null
        }

        constructor(mac: Mac?) {
            this.mac = mac
            cipher = null
            signature = null
        }
    }
}