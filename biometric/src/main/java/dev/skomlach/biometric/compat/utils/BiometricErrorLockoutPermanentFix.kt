package dev.skomlach.biometric.compat.utils

import android.content.SharedPreferences
import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider.getCryptoPreferences

@RestrictTo(RestrictTo.Scope.LIBRARY)
class BiometricErrorLockoutPermanentFix private constructor() {
    companion object {
        private const val TS_PREF = "user_unlock_device"
        @JvmField var INSTANCE = BiometricErrorLockoutPermanentFix()
    }
    private val sharedPreferences: SharedPreferences = getCryptoPreferences("BiometricErrorLockoutPermanentFix")
    fun setBiometricSensorPermanentlyLocked(type: BiometricType) {
        sharedPreferences.edit().putBoolean(TS_PREF + "-" + type.name, false).apply()
    }

    fun resetBiometricSensorPermanentlyLocked() {
        sharedPreferences.edit().clear().apply()
    }

    fun isBiometricSensorPermanentlyLocked(type: BiometricType): Boolean {
        return !sharedPreferences.getBoolean(TS_PREF + "-" + type.name, true)
    }
}