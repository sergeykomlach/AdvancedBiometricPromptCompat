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

import kotlin.math.pow

data class LinearSrgb(
    val r: Double,
    val g: Double,
    val b: Double,
) : Color {
    override fun toLinearSrgb() = this

    fun toSrgb(): Srgb {
        return Srgb(
            r = f(r),
            g = f(g),
            b = f(b),
        )
    }

    companion object {
        // Linear -> sRGB
        private fun f(x: Double) = if (x >= 0.0031308) {
            1.055 * x.pow(1.0 / 2.4) - 0.055
        } else {
            12.92 * x
        }

        // sRGB -> linear
        private fun fInv(x: Double) = if (x >= 0.04045) {
            ((x + 0.055) / 1.055).pow(2.4)
        } else {
            x / 12.92
        }

        fun Srgb.toLinearSrgb(): LinearSrgb {
            return LinearSrgb(
                r = fInv(r),
                g = fInv(g),
                b = fInv(b),
            )
        }
    }
}
