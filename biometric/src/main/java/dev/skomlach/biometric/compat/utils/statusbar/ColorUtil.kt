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

package dev.skomlach.biometric.compat.utils.statusbar

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * Common color utilities.
 *
 * @author [GeoSoft](mailto:info@geosoft.no)
 */

object ColorUtil {
    /**
     * Blend two colors.
     *
     * @param color1 First color to blend.
     * @param color2 Second color to blend.
     * @param ratio  Blend ratio. 0.5 will give even blend, 1.0 will return
     * color1, 0.0 will return color2 and so on.
     * @return Blended color.
     */
    fun blend(color1: Int, color2: Int, ratio: Double = 0.5): Int {
        val r = ratio.toFloat()
        val ir = 1.0.toFloat() - r
        return Color.argb(
            (Color.alpha(color1) * r + Color.alpha(color2) * ir).toInt(),
            (Color.red(color1) * r + Color.red(color2) * ir).toInt(),
            (Color.green(color1) * r + Color.green(color2) * ir).toInt(),
            (Color.blue(color1) * r + Color.blue(color2) * ir).toInt()
        )
    }

    /**
     * Return the "distance" between two colors. The rgb entries are taken
     * to be coordinates in a 3D space [0.0-1.0], and this method returnes
     * the distance between the coordinates for the first and second color.
     *
     * @param r1, g1, b1  First color.
     * @param r2, g2, b2  Second color.
     * @return Distance bwetween colors.
     */
    fun colorDistance(
        r1: Double, g1: Double, b1: Double,
        r2: Double, g2: Double, b2: Double
    ): Double {
        val a = r2 - r1
        val b = g2 - g1
        val c = b2 - b1
        return Math.sqrt(a * a + b * b + c * c)
    }

    /**
     * Return the "distance" between two colors.
     *
     * @param color1 First color.
     * @param color2 Second color.
     * @return Distance between colors.
     */
    fun colorDistance(color1: Int, color2: Int): Double {
        return colorDistance(
            (Color.red(color1) / 255.0f).toDouble(),
            (Color.green(color1) / 255.0f).toDouble(),
            (Color.blue(color1) / 255.0f).toDouble(),
            (
                    Color.red(color2) / 255.0f).toDouble(),
            (Color.green(color2) / 255.0f).toDouble(),
            (Color.blue(color2) / 255.0f).toDouble()
        )
    }

    /**
     * Check if a color is more dark than light. Useful if an entity of
     * this color is to be labeled: Use white label on a "dark" color and
     * black label on a "light" color.
     *
     * @param color Color to check.
     * @return True if this is a "dark" color, false otherwise.
     */
    fun isDark(color: Int): Boolean {
        return ColorUtils.calculateLuminance(color) < 0.5;
    }
}