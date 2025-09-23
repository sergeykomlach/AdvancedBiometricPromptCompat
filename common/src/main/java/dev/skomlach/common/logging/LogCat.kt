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

package dev.skomlach.common.logging

import android.util.Log
import dev.skomlach.common.BuildConfig


object LogCat {
    var DEBUG = BuildConfig.DEBUG
    fun logError(vararg msgs: Any?) {
        if (DEBUG) Log.e("LogCat", "${listOf(*msgs)}")
    }

    fun logException(e: Throwable) {
        if (DEBUG) {
            Log.e("LogCat", e.message, e)
        }
    }


    fun logException(e: Throwable?, vararg msgs: Any?) {
        if (DEBUG) {
            Log.e("LogCat", "${listOf(*msgs)}", e)
        }
    }


    fun log(vararg msgs: Any?) {
        if (DEBUG) {
            Log.d("LogCat", "${listOf(*msgs)}".toString())
        }
    }
}