package dev.skomlach.biometric.compat.utils.logging

import android.util.Log
import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.BuildConfig
import java.util.*

@RestrictTo(RestrictTo.Scope.LIBRARY)
object BiometricLoggerImpl {
    var DEBUG = BuildConfig.DEBUG
    @JvmStatic
    fun e(vararg msgs: Any?) {
        if (DEBUG) Log.e("BiometricLogging", listOf(*msgs).toString())
    }
    @JvmStatic
    fun e(e: Throwable) {
        e(e, e.message)
    }

    @JvmStatic
    fun e(e: Throwable?, vararg msgs: Any?) {
        if (DEBUG) Log.e("BiometricLogging", listOf(*msgs).toString(), e)
    }

    @JvmStatic
    fun d(vararg msgs: Any?) {
        if (DEBUG) Log.d("BiometricLogging", listOf(*msgs).toString())
    }
}