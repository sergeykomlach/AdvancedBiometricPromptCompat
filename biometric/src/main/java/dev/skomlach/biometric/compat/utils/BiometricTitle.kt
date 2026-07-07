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
import dev.skomlach.biometric.compat.R
import dev.skomlach.common.misc.SystemStringsHelper
import dev.skomlach.common.translate.LocalizationHelper

internal fun modalitySpecificPromptType(types: Set<BiometricType>): BiometricType? {
    val normalized = types.toMutableSet().apply {
        remove(BiometricType.BIOMETRIC_ANY)
    }
    if (normalized.size != 1) {
        return null
    }
    return when (val type = normalized.first()) {
        BiometricType.BIOMETRIC_FACE,
        BiometricType.BIOMETRIC_FINGERPRINT,
        BiometricType.BIOMETRIC_IRIS,
        BiometricType.BIOMETRIC_VOICE,
        BiometricType.BIOMETRIC_PALMPRINT,
        BiometricType.BIOMETRIC_HEARTRATE,
        BiometricType.BIOMETRIC_BEHAVIOR -> type

        else -> null
    }
}

object BiometricTitle {
    fun getRelevantTitle(context: Context, types: Set<BiometricType>): String {
        return when (modalitySpecificPromptType(types)) {
            BiometricType.BIOMETRIC_FACE -> {
                try {
                    context.getString(androidx.biometric.R.string.face_prompt_message)
                } catch (_: Exception) {
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_face_dialog_default_subtitle
                    )
                }
            }

            BiometricType.BIOMETRIC_FINGERPRINT -> {
                try {
                    context.getString(androidx.biometric.R.string.fingerprint_prompt_message)
                } catch (_: Exception) {
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_fingerprint_dialog_default_subtitle
                    )
                }
            }

            BiometricType.BIOMETRIC_IRIS -> {
                getSystemTitle(context, "iris")
                    ?: LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_biometric_dialog_default_subtitle
                    )
            }

            BiometricType.BIOMETRIC_VOICE -> {
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_voice_dialog_default_subtitle
                )
            }

            BiometricType.BIOMETRIC_PALMPRINT -> {
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_palmprint_dialog_default_subtitle
                )
            }

            BiometricType.BIOMETRIC_HEARTRATE -> {
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_heartrate_dialog_default_subtitle
                )
            }

            BiometricType.BIOMETRIC_BEHAVIOR -> {
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_behavior_dialog_default_subtitle
                )
            }

            else -> {
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_biometric_dialog_default_subtitle
                )
            }
        }
    }

    private fun getSystemTitle(context: Context, alias: String): String? {
        return getFromSystemTitle(context, alias) ?: getFromSystemSubtitle(context, alias)
    }

    private fun getFromSystemSubtitle(context: Context, alias: String): String? {
        return SystemStringsHelper.getFromSystem(context, alias + "_dialog_default_subtitle")
    }

    private fun getFromSystemTitle(context: Context, alias: String): String? {
        return SystemStringsHelper.getFromSystem(context, alias + "_dialog_default_title")
    }
}
