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

package dev.skomlach.biometric.compat.engine.internal

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.biometricErrorWithCodeDescription
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.biometricStartAuthenticationDescription
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.utils.BiometricLockoutFix
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.storage.SharedPreferenceProvider.getPreferences
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

abstract class AbstractBiometricModule(val biometricMethod: BiometricMethod) : BiometricModule {
    companion object {
        private const val ENROLLED_PREF = "enrolled_"
        private val enrollmentStores = ConcurrentHashMap<Int, EnrollmentBaselineStore>()
        internal var DEBUG_MANAGERS = false
        private val myUserIdMethod: Method? by lazy {
            runCatching {
                UserHandle::class.java.getMethod("myUserId")
            }.getOrNull()
        }
    }


    private val tag: Int = biometricMethod.id
    private val preferences: SharedPreferences = getPreferences("BiometricCompat_AbstractModule")
    protected var originalCancellationSignal: CancellationSignal? = null
    val name: String
        get() = javaClass.simpleName
    val context = AndroidContext.appContext
    var bundle: Bundle? = null
    override val isUserAuthCanByUsedWithCrypto: Boolean
        get() = false
    protected val authCallTimestamp = AtomicLong(0)

    private var cancelTask: Runnable? = null

    @Throws(Throwable::class)
    fun finalize() {
        cancelTask?.let {
            ExecutorHelper.removeCallbacks(it)
        }
    }

    protected fun postCancelTask(runnable: Runnable?) {
        cancelTask?.let {
            ExecutorHelper.removeCallbacks(it)
        }
        cancelTask = runnable
        ExecutorHelper.postDelayed(runnable ?: return, 2000)
    }

    protected fun getUserId(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                myUserIdMethod?.invoke(null) as? Int ?: 0
            } else {
                0
            }
        } catch (ignore: Throwable) {
            0
        }
    }

    protected fun lockout() {
        if (!isLockOut) {
            BiometricLockoutFix.lockout(biometricMethod.biometricType)
        }
    }

    protected fun startAuthenticationFailureDescription(): String {
        return biometricStartAuthenticationDescription()
    }

    protected fun authenticationErrorWithCodeDescription(errorCode: Int): String {
        return biometricErrorWithCodeDescription(errorCode)
    }

    override fun tag(): Int {
        return tag
    }

    override val isLockOut: Boolean
        get() {
            return BiometricLockoutFix.isLockOut(biometricMethod.biometricType)
        }

    abstract fun getManagers(): Set<Any>

    // Legacy external modules retain their contract through an isolated compatibility reader.
    internal open fun createEnrollmentReader(): () -> EnrollmentSnapshot =
        { LegacyEnrollmentReader.read(getManagers()) }

    private val enrollmentTracker by lazy {
        createEnrollmentReader().let { reader ->
            val prefs = preferences
            val key = "enrolled_v2_" + tag()
            val store = enrollmentStores.getOrPut(tag()) {
                EnrollmentBaselineStore(
                    readValue = {
                        if (prefs.contains(key)) decodeEnrollmentBaseline(prefs.getString(key, null)) else null
                    },
                    commitValue = { prefs.edit().putString(key, encodeEnrollmentBaseline(it)).commit() }
                )
            }
            EnrollmentChangeTracker(
                readSnapshot = reader,
                readBaseline = store::read,
                readLegacyBaseline = { prefs.getStringSet(ENROLLED_PREF + tag(), null)?.toSet() },
                writeBaseline = store::write,
                onError = { if (DEBUG_MANAGERS) e(it, "Hardware enrollment snapshot unavailable") }
            )
        }
    }

    @Deprecated("Enumeration may be unavailable; use system key invalidation for hardware protection")
    override val isBiometricEnrollChanged: Boolean
        get() = when (enrollmentTracker.check()) {
            EnrollmentChange.CHANGED -> true
            EnrollmentChange.UNCHANGED -> false
            EnrollmentChange.UNAVAILABLE, EnrollmentChange.UNSUPPORTED -> enrollmentTracker.lastConfirmedChange
        }

    open fun updateBiometricEnrollChanged() {
        enrollmentTracker.acknowledge()
    }

    protected fun restartCauseTimeout(reason: AuthenticationFailureReason?): Boolean {
        return reason == AuthenticationFailureReason.TIMEOUT
    }
}
