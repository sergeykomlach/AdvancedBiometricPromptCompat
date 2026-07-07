package dev.skomlach.biometric.compat.custom

import android.content.SharedPreferences
import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertEquals
import org.junit.Test

class AbstractSoftwareBiometricManagerLockoutTest {
    @Test
    fun forceLockoutImmediatelyActivatesTemporaryLockout() {
        val prefs = FakeSharedPreferences()
        val manager = TestManager()

        manager.forceImmediateLockout(
            prefs = prefs,
            maxFailedAttemptsBeforeLockout = 5,
            maxTemporaryLockoutsBeforePermanent = 3,
            lockoutDurationMs = 30_000L
        )

        assertEquals(
            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT,
            manager.currentLockoutError(
                prefs = prefs,
                maxFailedAttemptsBeforeLockout = 5,
                maxTemporaryLockoutsBeforePermanent = 3,
                lockoutDurationMs = 30_000L
            )
        )
    }

    @Test
    fun forceLockoutEscalatesToPermanentAfterConfiguredThreshold() {
        val prefs = FakeSharedPreferences()
        val manager = TestManager()

        repeat(2) {
            manager.forceImmediateLockout(
                prefs = prefs,
                maxFailedAttemptsBeforeLockout = 5,
                maxTemporaryLockoutsBeforePermanent = 2,
                lockoutDurationMs = 30_000L
            )
        }

        assertEquals(
            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT,
            manager.currentLockoutError(
                prefs = prefs,
                maxFailedAttemptsBeforeLockout = 5,
                maxTemporaryLockoutsBeforePermanent = 2,
                lockoutDurationMs = 30_000L
            )
        )
    }

    private class TestManager : AbstractSoftwareBiometricManager() {
        override fun getTimeoutMessage(): CharSequence? = null

        override fun resetLockOut() = Unit

        override fun resetPermanentLockOut() = Unit

        override fun getPermissions(): List<String> = emptyList()

        override val biometricType: BiometricType = BiometricType.BIOMETRIC_ANY

        override fun isHardwareDetected(): Boolean = true

        override fun hasEnrolledBiometric(): Boolean = true

        override fun getManagers(): Set<Any> = emptySet()

        override fun remove(extra: android.os.Bundle?) = Unit

        override fun getEnrollBundle(name: String?): android.os.Bundle = android.os.Bundle()

        override fun getEnrolls(): Collection<String> = emptyList()

        override fun authenticate(
            crypto: CryptoObject?,
            flags: Int,
            cancel: android.os.CancellationSignal?,
            callback: AuthenticationCallback?,
            handler: android.os.Handler?,
            extra: android.os.Bundle?
        ) = Unit

        fun forceImmediateLockout(
            prefs: SharedPreferences,
            maxFailedAttemptsBeforeLockout: Int,
            maxTemporaryLockoutsBeforePermanent: Int,
            lockoutDurationMs: Long
        ) {
            forceLockout(
                prefs,
                LockoutPolicy(
                    maxFailedAttemptsBeforeLockout = maxFailedAttemptsBeforeLockout,
                    maxTemporaryLockoutsBeforePermanent = maxTemporaryLockoutsBeforePermanent,
                    lockoutDurationMs = lockoutDurationMs
                )
            )
        }

        fun currentLockoutError(
            prefs: SharedPreferences,
            maxFailedAttemptsBeforeLockout: Int,
            maxTemporaryLockoutsBeforePermanent: Int,
            lockoutDurationMs: Long
        ): Int? {
            return getStoredLockoutError(
                prefs,
                LockoutPolicy(
                    maxFailedAttemptsBeforeLockout = maxFailedAttemptsBeforeLockout,
                    maxTemporaryLockoutsBeforePermanent = maxTemporaryLockoutsBeforePermanent,
                    lockoutDurationMs = lockoutDurationMs
                )
            )
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = LinkedHashMap<String, Any>()

        override fun getAll(): MutableMap<String, *> = LinkedHashMap(data)

        override fun getString(key: String?, defValue: String?): String? =
            data[key] as? String ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            ((data[key] as? Set<String>)?.toMutableSet()) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            data[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor(data)

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        private class Editor(
            private val target: LinkedHashMap<String, Any>
        ) : SharedPreferences.Editor {
            private val updates = LinkedHashMap<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                updates[key.orEmpty()] = value
            }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?
            ): SharedPreferences.Editor = apply {
                updates[key.orEmpty()] = values?.toSet()
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                updates[key.orEmpty()] = value
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                updates[key.orEmpty()] = value
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                updates[key.orEmpty()] = value
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                updates[key.orEmpty()] = value
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                updates[key.orEmpty()] = null
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearRequested = true
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearRequested) {
                    target.clear()
                }
                updates.forEach { (key, value) ->
                    if (value == null) {
                        target.remove(key)
                    } else {
                        target[key] = value
                    }
                }
            }
        }
    }
}
