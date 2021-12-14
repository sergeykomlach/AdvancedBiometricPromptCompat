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

import android.content.SharedPreferences
import android.os.Build
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider.getCryptoPreferences


object BiometricAuthWasCanceledByError {
    private const val TS_PREF = "error_cancel"
    private val preferences: SharedPreferences = getCryptoPreferences("BiometricCompat_AuthWasCanceledByError")
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