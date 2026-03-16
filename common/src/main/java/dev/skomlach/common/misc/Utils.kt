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

package dev.skomlach.common.misc

import android.annotation.SuppressLint
import androidx.core.os.BuildCompat
import dev.skomlach.common.R
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.translate.LocalizationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object Utils {
    init {
        prefetchStrings()
    }

    fun prefetchStrings() {
        try {
            val stringIds: Array<Int> = R.string::class.java
                .fields
                .asSequence()
                .filter { it.type == Int::class.javaPrimitiveType }
                .filter { it.name.startsWith("biometriccompat_") }
                .mapNotNull { field ->
                    try {
                        field.getInt(null)
                    } catch (_: Throwable) {
                        null
                    }
                }
                .toList()
                .toTypedArray()
            LogCat.log("Utils", "LocalizationHelper.prefetch")

            var prefech: Job? = null
            prefech = GlobalScope.launch(Dispatchers.IO) {
                LocalizationHelper.prefetch(
                    AndroidContext.appContext,
                    *stringIds
                )
            }
            GlobalScope.launch(Dispatchers.Main) {
                AndroidContext.configurationLiveData.observeForever {
                    LogCat.log(
                        "Utils",
                        "observeForever -> LocalizationHelper.prefetch"
                    )
                    prefech?.cancel()
                    prefech = GlobalScope.launch(Dispatchers.IO) {
                        LocalizationHelper.prefetch(
                            AndroidContext.appContext,
                            *stringIds
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

    val isAtLeastU: Boolean
        @SuppressLint("UnsafeOptInUsageError")
        get() = BuildCompat.isAtLeastU()
    val isAtLeastT: Boolean
        @SuppressLint("UnsafeOptInUsageError")
        get() = BuildCompat.isAtLeastT()

    val isAtLeastR: Boolean
        get() = BuildCompat.isAtLeastR()

    val isAtLeastS: Boolean
        get() = BuildCompat.isAtLeastS()
}