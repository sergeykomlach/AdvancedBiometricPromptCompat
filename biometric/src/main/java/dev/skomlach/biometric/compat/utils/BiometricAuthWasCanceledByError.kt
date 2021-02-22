package dev.skomlach.biometric.compat.utils

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RestrictTo
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider.getCryptoPreferences

@RestrictTo(RestrictTo.Scope.LIBRARY)
class BiometricAuthWasCanceledByError private constructor() {
    companion object {
        private const val TS_PREF = "error_cancel"
        @JvmField var INSTANCE = BiometricAuthWasCanceledByError()
    }
    private val preferences: SharedPreferences = getCryptoPreferences("BiometricModules")
    fun setCanceledByError() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) preferences.edit()
            .putBoolean(TS_PREF, true).apply()
    }

    fun resetCanceledByError() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) preferences.edit()
            .putBoolean(TS_PREF, false).apply()
    }

    val isCanceledByError: Boolean
        get() = Build.VERSION.SDK_INT == Build.VERSION_CODES.P && preferences.getBoolean(
            TS_PREF,
            false
        )


}