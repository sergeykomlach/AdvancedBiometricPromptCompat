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

package dev.skomlach.biometric.compat.utils

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.Uri
import android.os.Build
import dev.skomlach.common.contextprovider.AndroidContext
import java.util.*

@SuppressLint("PrivateApi")

object LockType {
    private val appContext = AndroidContext.appContext

    /**
     * The bit in LOCK_BIOMETRIC_WEAK_FLAGS to be used to indicate whether liveliness should be used
     */
    const val FLAG_BIOMETRIC_WEAK_LIVELINESS = 0x1

    /**
     * A flag containing settings used for biometric weak
     *
     * @hide
     */
    private const val LOCK_BIOMETRIC_WEAK_FLAGS = "lock_biometric_weak_flags"
    private const val PASSWORD_TYPE_KEY = "lockscreen.password_type"
    private const val PASSWORD_TYPE_ALTERNATE_KEY = "lockscreen.password_type_alternate"

    /**
     * @return Whether the biometric weak liveliness is enabled.
     */

    fun isBiometricWeakLivelinessEnabled(context: Context): Boolean {
        val currentFlag = SettingsHelper.getLong(context, LOCK_BIOMETRIC_WEAK_FLAGS, 0L)
        return currentFlag and FLAG_BIOMETRIC_WEAK_LIVELINESS.toLong() != 0L
    }


    private fun getClass(pkg: String?): Class<*> {
        return try {
            ReflectionTools.getClassFromPkg(
                pkg ?: return Class.forName("com.android.internal.widget.LockPatternUtils"),
                "com.android.internal.widget.LockPatternUtils"
            )
        } catch (e: Throwable) {
            Class.forName("com.android.internal.widget.LockPatternUtils")
        }
    }

    fun isBiometricWeakEnabled(pkg: String?, context: Context): Boolean {
        try {
            val mode: Int
            val lockUtilsClass = getClass(pkg)
            val lockUtils =
                lockUtilsClass.getConstructor(Context::class.java).newInstance(appContext)

            val method = lockUtilsClass.getMethod("getActivePasswordQuality")
            mode = method.invoke(lockUtils) as Int
            return mode == DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK
        } catch (ignore: Throwable) {

        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            try {
                val mode: Int
                val lockUtilsClass = getClass(pkg)
                val lockUtils =
                    lockUtilsClass.getConstructor(Context::class.java).newInstance(appContext)

                val method = lockUtilsClass.getMethod(
                    "getActivePasswordQuality",
                    Int::class.javaPrimitiveType
                )
                mode = method.invoke(lockUtils, 0) as Int
                return mode == DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK
            } catch (ignore: Throwable) {

            }
        return isBiometricEnabledInSettings(context)
    }

    fun isBiometricEnabledInSettings(context: Context, type: String): Boolean {
        try {
            val keyValue: MutableList<String> = ArrayList()
            val u = Uri.parse("content://settings/secure")
            var mCur = context
                .contentResolver
                .query(
                    u, null,
                    null,
                    null,
                    null
                )
            if (mCur != null) {
                mCur.moveToFirst()
                while (!mCur.isAfterLast) {
                    val nameIndex = mCur
                        .getColumnIndex("name")
                    if (!mCur.isNull(nameIndex)) {
                        val name = mCur.getString(nameIndex)
                        if (name.isNullOrEmpty()) {
                            mCur.moveToNext()
                            continue
                        }
                        val s = name.lowercase(Locale.ROOT)
                        if (s.contains(type)) {
                            if (s.contains("_unl") && s.contains("_enable")) {
                                keyValue.add(name)
                            }
                        }
                    }
                    mCur.moveToNext()
                }
                mCur.close()
                mCur = null
            }
            for (s in keyValue) {
                //-1 not exists, 0 - disabled
                if (SettingsHelper.getInt(context, s, -1) > 0) {
                    return true
                }
            }
        } catch (ignored: Throwable) {
        }
        return false
    }

    fun isBiometricEnabledInSettings(context: Context): Boolean {
        try {
            val keyValue: MutableList<String> = ArrayList()
            val u = Uri.parse("content://settings/secure")
            var mCur = context
                .contentResolver
                .query(
                    u, null,
                    null,
                    null,
                    null
                )
            if (mCur != null) {
                mCur.moveToFirst()
                while (!mCur.isAfterLast) {
                    val nameIndex = mCur
                        .getColumnIndex("name")
                    if (!mCur.isNull(nameIndex)) {
                        val name = mCur.getString(nameIndex)
                        if (name.isNullOrEmpty()) {
                            mCur.moveToNext()
                            continue
                        }
                        val s = name.lowercase(Locale.ROOT)
                        if (s.contains("fingerprint")
                            || s.contains("face")
                            || s.contains("iris")
                            || s.contains("biometric")
                        ) {
                            if (s.contains("_unl") && s.contains("_enable")) {
                                keyValue.add(name)
                            }
                        }
                    }
                    mCur.moveToNext()
                }
                mCur.close()
                mCur = null
            }
            for (s in keyValue) {
                //-1 not exists, 0 - disabled
                if (SettingsHelper.getInt(context, s, -1) > 0) {
                    return true
                }
            }
        } catch (ignored: Throwable) {
        }
        val pwrdType = SettingsHelper.getLong(
            context,
            PASSWORD_TYPE_KEY,
            DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED.toLong()
        )
        val pwrdAltType = SettingsHelper.getLong(
            context,
            PASSWORD_TYPE_ALTERNATE_KEY,
            DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED.toLong()
        )
        return pwrdType == DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK.toLong() ||
                pwrdAltType == DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK.toLong()
    }
}