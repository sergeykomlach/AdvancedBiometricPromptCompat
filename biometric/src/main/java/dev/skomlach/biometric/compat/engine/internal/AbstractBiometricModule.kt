package dev.skomlach.biometric.compat.engine.internal

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.engine.BiometricCodes
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider.getCryptoPreferences
import java.util.concurrent.TimeUnit

@RestrictTo(RestrictTo.Scope.LIBRARY)
abstract class AbstractBiometricModule(val biometricMethod: BiometricMethod) : BiometricModule,
    BiometricCodes {
    companion object {
        //LockOut behavior emulated, because for example Meizu API allow to enroll fingerprint unlimited times
        private const val TS_PREF = "timestamp_"
        private val timeout = TimeUnit.SECONDS.toMillis(31)
    }
    private val tag: Int = biometricMethod.id
    private val preferences: SharedPreferences = getCryptoPreferences("BiometricModules")
    val name: String
        get() = javaClass.simpleName
    val context: Context
        get() = appContext

    fun lockout() {
        if (!isLockOut) {
            d(name + ": setLockout for " + tag())
            preferences.edit().putLong(TS_PREF + tag(), System.currentTimeMillis()).apply()
        }
    }

    override fun tag(): Int {
        return tag
    }

    override val isLockOut: Boolean
        get() {
            val ts = preferences.getLong(TS_PREF + tag(), 0)
            return if (ts > 0) {
                if (System.currentTimeMillis() - ts >= timeout) {
                    preferences.edit().putLong(TS_PREF + tag(), 0).apply()
                    d(name + ": lockout is FALSE(1) for " + tag())
                    false
                } else {
                    d(name + ": lockout is TRUE for " + tag())
                    true
                }
            } else {
                d(name + ": lockout is FALSE(2) for " + tag())
                false
            }
        }

}