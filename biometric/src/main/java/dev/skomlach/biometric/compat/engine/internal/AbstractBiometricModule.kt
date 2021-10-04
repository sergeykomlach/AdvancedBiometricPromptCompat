  /*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.BuildConfig
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.BiometricCodes
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.utils.HexUtils
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider.getCryptoPreferences
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

  @RestrictTo(RestrictTo.Scope.LIBRARY)
abstract class AbstractBiometricModule(val biometricMethod: BiometricMethod) : BiometricModule,
    BiometricCodes {
    companion object {
        //LockOut behavior emulated, because for example Meizu API allow to enroll fingerprint unlimited times
        private const val TS_PREF = "timestamp_"
        private const val ENROLLED_PREF = "enrolled_"
        private val timeout = TimeUnit.SECONDS.toMillis(31)
        var DEBUG_MANAGERS = BuildConfig.DEBUG
    }
    private var firstTimeout : Long? = null
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

      abstract fun getManagers(): Set<Any>
      override val isBiometricEnrollChanged: Boolean
          get() {
              val lastKnown = preferences.getStringSet(
                  ENROLLED_PREF + tag(),
                  null
              )
              if(lastKnown == null){
                  updateBiometricEnrollChanged()
                  return false
              }
              return getHashes() != lastKnown
          }

      fun updateBiometricEnrollChanged() {
         preferences.edit().putStringSet(ENROLLED_PREF + tag(), getHashes()).apply()
      }

      open fun getIds(manager: Any): List<String> {
          val ids = ArrayList<String?>()
          try {
              val method = manager.javaClass.declaredMethods.firstOrNull {
                  it.name.contains(
                      "enrolled",
                      ignoreCase = true
                  ) && it.returnType != Void.TYPE && it.parameterTypes.isEmpty()
              }
              val isAccessible = method?.isAccessible ?: true
              if (!isAccessible)
                  method?.isAccessible = true
              try {
                  method?.invoke(manager)?.let { result ->
                      when (result) {
                          is Collection<*> -> {
                              for (i in result) {
                                  i?.let {
                                      ids.add(getUniqueId(it))
                                  }
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
          } catch (e: Throwable) {
              e(e)
          }
          return ids.filterNotNull()
      }

      private fun getHashes(): Set<String> {
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

          if (result is String)
              return result

          try {
              val stringBuilder = StringBuilder()
              val fields = result.javaClass.declaredMethods.filter {
                  it.name.endsWith(
                      "id",
                      ignoreCase = true
                  ) && it.returnType != Void.TYPE && it.parameterTypes.isEmpty()
              }
              if (fields.isNotEmpty()) {
                  stringBuilder.append(result.javaClass.simpleName)
                  for (f in fields) {
                      val isAccessible = f.isAccessible
                      if (!isAccessible)
                          f.isAccessible = true
                      try {
                          stringBuilder.append("; ").append(f.name).append("=")
                              .append(f.invoke(result))
                      } finally {
                          if (!isAccessible)
                              f.isAccessible = false
                      }
                  }
              }
              val s = stringBuilder.toString().trim()
              if (s.isNotEmpty())
                  return s
          } catch (e: Throwable) {
              e(e)
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
      fun restartCauseTimeout(reason: AuthenticationFailureReason?) : Boolean{
          if(reason == AuthenticationFailureReason.TIMEOUT){
              val current = System.currentTimeMillis()
              return if(firstTimeout == null){
                  firstTimeout = current
                  true
              }else {
                  val safeTimeout = current - (firstTimeout?:return false) <= TimeUnit.SECONDS.toMillis(30)
                  if(!safeTimeout) {
                      firstTimeout = null
                  }
                  safeTimeout
              }
          }

          firstTimeout = null
          return false
      }
  }