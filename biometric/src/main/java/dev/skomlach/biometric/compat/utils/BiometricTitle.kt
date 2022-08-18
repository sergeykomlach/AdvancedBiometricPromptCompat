/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

import android.content.Context
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.Utils

object BiometricTitle {
    fun getRelevantTitle(context: Context, types: Set<BiometricType>): String {
        //Attempt#1
        val set = types.toMutableSet().apply {
            remove(BiometricType.BIOMETRIC_ANY)
        }
        if (set.size == 1 && set.contains(BiometricType.BIOMETRIC_FACE)) {
            getSystemTitle(context, "face")?.let {
                return it
            }
            return context
                .getString(androidx.biometric.R.string.face_prompt_message)
        } else if (set.size == 1 && set.contains(BiometricType.BIOMETRIC_IRIS))
            getSystemTitle(context, "iris")?.let {
                return it
            }
        else if (set.size == 1 && set.contains(BiometricType.BIOMETRIC_FINGERPRINT)) {
            getSystemTitle(context, "fingerprint")?.let {
                return it
            }
            return context
                .getString(androidx.biometric.R.string.fingerprint_prompt_message)
        } else if (set.size == 1 && set.contains(BiometricType.BIOMETRIC_VOICE))
            getSystemTitle(context, "voice")?.let {
                return it
            }

        //Attempt#2
        try {
            if (Utils.isAtLeastS) {
                var biometricManager: android.hardware.biometrics.BiometricManager? =
                    context.getSystemService(
                        android.hardware.biometrics.BiometricManager::class.java
                    )

                if (biometricManager == null) {
                    biometricManager = context.getSystemService(
                        Context.BIOMETRIC_SERVICE
                    ) as android.hardware.biometrics.BiometricManager?
                }
                val strings =
                    biometricManager?.getStrings(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                val prompt = strings?.promptMessage
                if (!prompt.isNullOrEmpty())
                    return prompt.toString()
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }

        getSystemTitle(context, "biometric")?.let {
            return it
        }
        //Give up
        return context
            .getString(androidx.biometric.R.string.biometric_prompt_message)
    }

    private fun getSystemTitle(context: Context, alias: String): String? {
        return getFromSystemTitle(context, alias) ?: getFromSystemSubtitle(context, alias)
    }

    private fun getFromSystemSubtitle(context: Context, alias: String): String? {
        try {
            val fields = Class.forName("com.android.internal.R\$string").declaredFields
            for (field in fields) {
                if (field.name.equals(alias + "_dialog_default_subtitle")) {
                    BiometricLoggerImpl.d("BiometricTitle", field.name)
                    val isAccessible = field.isAccessible
                    return try {
                        if (!isAccessible) field.isAccessible = true
                        val s = context.getString(field[null] as Int)
                        if (s.isEmpty())
                            throw RuntimeException("String is empty")
                        s
                    } finally {
                        if (!isAccessible) field.isAccessible = false
                    }
                }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        return null
    }

    private fun getFromSystemTitle(context: Context, alias: String): String? {
        try {
            val fields = Class.forName("com.android.internal.R\$string").declaredFields
            for (field in fields) {
                if (field.name.equals(alias + "_dialog_default_title")) {
                    BiometricLoggerImpl.d("BiometricTitle", field.name)
                    val isAccessible = field.isAccessible
                    return try {
                        if (!isAccessible) field.isAccessible = true
                        val s = context.getString(field[null] as Int)
                        if (s.isEmpty())
                            throw RuntimeException("String is empty")
                        s
                    } finally {
                        if (!isAccessible) field.isAccessible = false
                    }
                }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        return null
    }
}