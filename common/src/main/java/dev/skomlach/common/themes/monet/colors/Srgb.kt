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

package dev.skomlach.common.themes.monet.colors

import kotlin.math.roundToInt
import  dev.skomlach.common.themes.monet.colors.LinearSrgb.Companion.toLinearSrgb as realToLinearSrgb

data class Srgb(
    val r: Double,
    val g: Double,
    val b: Double,
) : Color {
    // Convenient constructors for quantized values
    constructor(r: Int, g: Int, b: Int) : this(
        r.toDouble() / 255.0,
        g.toDouble() / 255.0,
        b.toDouble() / 255.0,
    )

    constructor(color: Int) : this(
        android.graphics.Color.red(color),
        android.graphics.Color.green(color),
        android.graphics.Color.blue(color),
    )

    override fun toLinearSrgb() = realToLinearSrgb()

    fun quantize8(): Int {
        return android.graphics.Color.rgb(
            quantize8(r),
            quantize8(g),
            quantize8(b),
        )
    }

    companion object {
        // Clamp out-of-bounds values
        private fun quantize8(n: Double) = (n * 255.0).roundToInt().coerceIn(0, 255)
    }
}
