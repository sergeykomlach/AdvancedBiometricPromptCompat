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

package dev.skomlach.biometric.compat.utils.logging

import android.util.Log
import dev.skomlach.biometric.compat.BuildConfig


object BiometricLoggerImpl {
    var DEBUG = BuildConfig.DEBUG

    fun e(vararg msgs: Any?) {
        if (DEBUG) Log.e("BiometricLogging", listOf(*msgs).toString())
    }

    fun e(e: Throwable) {
        e(e, e.message)
    }


    fun e(e: Throwable?, vararg msgs: Any?) {
        if (DEBUG) Log.e("BiometricLogging", listOf(*msgs).toString(), e)
    }


    fun d(vararg msgs: Any?) {
        if (DEBUG) Log.d("BiometricLogging", listOf(*msgs).toString())
    }
}