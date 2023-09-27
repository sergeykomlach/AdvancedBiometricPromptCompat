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
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.utils.BiometricLockoutFix
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.HexUtils
import dev.skomlach.common.storage.SharedPreferenceProvider.getPreferences
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

abstract class AbstractBiometricModule(val biometricMethod: BiometricMethod) : BiometricModule {
    companion object {
        private const val ENROLLED_PREF = "enrolled_"
        var DEBUG_MANAGERS = false
    }

    private var firstTimeout: Long? = null
    private val tag: Int = biometricMethod.id
    private val preferences: SharedPreferences = getPreferences("BiometricCompat_AbstractModule")
    protected var originalCancellationSignal: CancellationSignal? = null
    val name: String
        get() = javaClass.simpleName
    val context = AndroidContext.appContext
    var bundle: Bundle? = null
    override val isUserAuthCanByUsedWithCrypto: Boolean
        get() = true
    protected val authCallTimestamp = AtomicLong(0)

    private var cancelTask: Runnable? = null

    @Throws(Throwable::class)
    fun finalize() {
        cancelTask?.let {
            ExecutorHelper.removeCallbacks(it)
        }
    }

    fun postCancelTask(runnable: Runnable?) {
        cancelTask?.let {
            ExecutorHelper.removeCallbacks(it)
        }
        cancelTask = runnable
        ExecutorHelper.postDelayed(runnable ?: return, 2000)
    }

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

    abstract fun getManagers(): Set<Any>

    override val isBiometricEnrollChanged: Boolean
        get() {
            val lastKnown = preferences.getStringSet(
                ENROLLED_PREF + tag(),
                emptySet()
            ) ?: emptySet()
            if (lastKnown.isEmpty()) {
                updateBiometricEnrollChanged()
                return false
            }
            return getHashes().toMutableList().apply {
                removeAll(lastKnown)
            }.isNotEmpty()
        }

    fun updateBiometricEnrollChanged() {
        preferences.edit().putStringSet(ENROLLED_PREF + tag(), getHashes()).apply()
    }

    open fun getIds(manager: Any): List<String> {
        val ids = ArrayList<String?>()
        try {
            val methods = manager.javaClass.declaredMethods.filter {
                (it.name.contains(
                    "enrolled",
                    ignoreCase = true
                ) || it.name.contains(
                    "registered",
                    ignoreCase = true
                )) && it.returnType != Void.TYPE && it.parameterTypes.isEmpty()
            }
            methods.forEach { method ->
                val isAccessible = method?.isAccessible ?: true
                try {
                    if (!isAccessible)
                        method?.isAccessible = true
                    method?.invoke(manager)?.let { result ->
                        when (result) {
                            is List<*> -> {
                                for (i in result) {
                                    i?.let {
                                        ids.add(getUniqueId(it))
                                    }
                                }
                            }

                            is Collection<*> -> {
                                for (i in result) {
                                    i?.let {
                                        ids.add(getUniqueId(it))
                                    }
                                }
                            }

                            is IntArray -> {
                                for (i in result) {
                                    e("$name: Int ids $i")
                                    ids.add(getUniqueId(i))
                                }
                            }

                            is LongArray -> {
                                for (i in result) {
                                    e("$name: Long ids $i")
                                    ids.add(getUniqueId(i))
                                }
                            }

                            is Array<*> -> {
                                for (i in result)
                                    i?.let {
                                        ids.add(getUniqueId(it))
                                    }
                            }

                            else -> {
                                ids.add(getUniqueId(result))
                            }
                        }
                    }
                } finally {
                    if (!isAccessible)
                        method?.isAccessible = false
                }
                if (ids.filterNotNull().isNotEmpty()) return ids.filterNotNull()
            }
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e("$name", e)
        }
        return emptyList()
    }

    fun getHashes(): Set<String> {
        val hashes = HashSet<String>()
        getManagers().let {
            val ids = ArrayList<String>()
            for (manager in it) {
                ids.addAll(getIds(manager))
            }
            val temp = HashSet<String>()
            val countMatches = HashMap<String, Int>()
            for (i in ids) {
                countMatches[i] = countMatches[i]?.plus(1) ?: 0
            }
            for (key in countMatches.keys) {
                val matches = countMatches[key] ?: 0
                if (matches == 0) {
                    temp.add(key)
                } else {
                    for (value in 0..matches) {
                        temp.add("$key; index=$value")
                    }
                }
            }


            for (id in temp) {
                md5(id)?.let { md5 ->
                    e("$name: getHashes $id -> $md5")
                    hashes.add(md5)
                }
            }
        }

        return hashes
    }

    private fun getUniqueId(result: Any): String? {
        if (result is Int)
            return "$result"
        if (result is Long)
            return "$result"
        if (result is String)
            return result

        try {
            val stringBuilder = StringBuilder()
            val methods = result.javaClass.declaredMethods.filter {
                (it.name.endsWith(
                    "id",
                    ignoreCase = true
                ) && it.returnType != Void.TYPE && it.parameterTypes.isEmpty()) || (it.name.endsWith(
                    "name",
                    ignoreCase = true
                ) && it.returnType != Void.TYPE && it.parameterTypes.isEmpty())
            }.toMutableList().apply {
                this.addAll(result.javaClass.superclass.declaredMethods.filter {
                    (it.name.endsWith(
                        "id",
                        ignoreCase = true
                    ) && it.returnType != Void.TYPE && it.parameterTypes.isEmpty()) || (it.name.endsWith(
                        "name",
                        ignoreCase = true
                    ) && it.returnType != Void.TYPE && it.parameterTypes.isEmpty())
                })
            }.toSet()

            for (f in methods) {
                val isAccessible = f.isAccessible
                if (!isAccessible)
                    f.isAccessible = true
                try {
                    f.invoke(result) ?: continue
                    if (stringBuilder.isEmpty())
                        stringBuilder.append(result.javaClass.simpleName).append("; ")
                    stringBuilder.append(f.name).append("=")
                        .append(f.invoke(result)).append("; ")
                } finally {
                    if (!isAccessible)
                        f.isAccessible = false
                }
            }
            val s = stringBuilder.toString().trim()
            if (s.isNotEmpty())
                return s
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e("$name", e)
        }
        return null
    }

    private fun md5(s: String): String? {
        try {
            val digest = MessageDigest.getInstance("MD5")
            digest.reset()
            digest.update(s.toByteArray(Charset.forName("UTF-8")))
            return HexUtils.bytesToHex(digest.digest())
        } catch (e: Exception) {

        }
        return null
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