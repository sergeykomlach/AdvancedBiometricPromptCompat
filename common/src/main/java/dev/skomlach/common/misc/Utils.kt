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
import android.content.Context
import android.content.Intent
import androidx.core.os.BuildCompat
import dev.skomlach.common.logging.LogCat
import java.util.*

object Utils {
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

    fun startActivity(intent: Intent, context: Context): Boolean {
        try {
            if (intentCanBeResolved(intent, context)) {
                context.startActivity(
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            }
        } catch (throwable: Throwable) {
            LogCat.logException(throwable)
        }
        return false
    }

    private fun intentCanBeResolved(intent: Intent, context: Context): Boolean {
        val pm = context.packageManager
        val pkgAppsList = pm.queryIntentActivities(intent, 0)
        return pkgAppsList.size > 0
    }


    fun checkClass(className: String): Boolean {
        try {
            return Class.forName(className) != null
        } catch (e: Throwable) {
        }
        return false
    }
}