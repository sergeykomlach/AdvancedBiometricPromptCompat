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
import kotlin.math.roundToInt

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
    fun blend(color1: Int, color2: Int, ratio: Double): Int {
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
     * Make an even blend between two colors.
     *
     * @param color1 First color to blend.
     * @param color2 Second color to blend.
     * @return Blended color.
     */
    fun blend(color1: Int, color2: Int): Int {
        return blend(color1, color2, 0.5)
    }

    /**
     * Make a color darker.
     *
     * @param color    Color to make darker.
     * @param fraction Darkness fraction.
     * @return Darker color.
     */
    fun darker(color: Int, fraction: Double): Int {
        var red = (Color.red(color) * (1.0 - fraction)).roundToInt()
        var green = (Color.green(color) * (1.0 - fraction)).roundToInt()
        var blue = (Color.blue(color) * (1.0 - fraction)).roundToInt()
        if (red < 0) red = 0 else if (red > 255) red = 255
        if (green < 0) green = 0 else if (green > 255) green = 255
        if (blue < 0) blue = 0 else if (blue > 255) blue = 255
        val alpha = Color.alpha(color)
        return Color.argb(alpha, red, green, blue)
    }

    /**
     * Make a color lighter.
     *
     * @param color    Color to make lighter.
     * @param fraction Darkness fraction.
     * @return Lighter color.
     */
    fun lighter(color: Int, fraction: Double): Int {
        var red = (Color.red(color) * (1.0 + fraction)).roundToInt()
        var green = (Color.green(color) * (1.0 + fraction)).roundToInt()
        var blue = (Color.blue(color) * (1.0 + fraction)).roundToInt()
        if (red < 0) red = 0 else if (red > 255) red = 255
        if (green < 0) green = 0 else if (green > 255) green = 255
        if (blue < 0) blue = 0 else if (blue > 255) blue = 255
        val alpha = Color.alpha(color)
        return Color.argb(alpha, red, green, blue)
    }

    /**
     * Return the hex name of a specified color.
     *
     * @param color Color to get hex name of.
     * @return Hex name of color: "rrggbb".
     */
    fun getHexName(color: Int): String {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val rHex = r.toString(16)
        val gHex = g.toString(16)
        val bHex = b.toString(16)
        return (if (rHex.length == 2) "" + rHex else "0$rHex") +
                (if (gHex.length == 2) "" + gHex else "0$gHex") +
                if (bHex.length == 2) "" + bHex else "0$bHex"
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
     * @param color1 First color [r,g,b].
     * @param color2 Second color [r,g,b].
     * @return Distance bwetween colors.
     */
    fun colorDistance(color1: DoubleArray, color2: DoubleArray): Double {
        return colorDistance(
            color1[0], color1[1], color1[2],
            color2[0], color2[1], color2[2]
        )
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
        val tmpHsl = FloatArray(3)
        ColorUtils.colorToHSL(color, tmpHsl)
        return tmpHsl[2] < 0.45f
    }

    fun trueDarkColor(c: Int): Boolean {
        var color = c
        val ratio = 0.9 //keep X of origin color
        var isDark = isDark(blend(color, Color.GRAY))
        color = if (isDark) {
            blend(color, Color.WHITE, ratio)
        } else {
            blend(color, Color.BLACK, ratio)
        }
        isDark = isDark(color)
        return isDark
    }
}