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
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.monet.SystemColorScheme
import dev.skomlach.biometric.compat.utils.monet.toArgb
import dev.skomlach.common.misc.Utils

object DialogMainColor {
    @ColorInt
    fun getColor(context: Context, isNightMode: Boolean): Int {
        return if (Utils.isAtLeastS) {
            val monetColors = SystemColorScheme(context)
            if (isNightMode) monetColors.neutral1[900]!!.toArgb()
            else
                monetColors.neutral1[50]!!.toArgb()
        } else {
            if (isNightMode) {
                ContextCompat.getColor(context, R.color.black)
            } else {
                ContextCompat.getColor(context, R.color.material_grey_50)
            }
        }
    }
}