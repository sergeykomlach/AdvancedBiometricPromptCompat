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

package dev.skomlach.common.themes
/*
* See https://stackoverflow.com/a/56515949
* */

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

// Decides when dark theme is optimal for this wallpaper
private const val DARK_THEME_MEAN_LUMINANCE = 0.25f

// Minimum mean luminosity that an image needs to have to support dark text
private const val BRIGHT_IMAGE_MEAN_LUMINANCE = 0.75f

// We also check if the image has dark pixels in it,
// to avoid bright images with some dark spots.
private const val DARK_PIXEL_LUMINANCE = 0.45f
private const val MAX_DARK_AREA = 0.05f

/**
 * Specifies that dark text is preferred over the current wallpaper for best presentation.
 *
 *
 * eg. A launcher may set its text color to black if this flag is specified.
 * @hide
 */
private const val HINT_SUPPORTS_DARK_TEXT = 1 shl 0

/**
 * Specifies that dark theme is preferred over the current wallpaper for best presentation.
 *
 *
 * eg. A launcher may set its drawer color to black if this flag is specified.
 * @hide
 */
private const val HINT_SUPPORTS_DARK_THEME = 1 shl 1

fun getIsOsDarkTheme(context: Context): DarkThemeCheckResult {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O)
        return DarkThemeCheckResult.UNDEFINED
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val wallpaperColors =
            wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?: return DarkThemeCheckResult.UNDEFINED
        var darkHints: Int
        try {
            val m = WallpaperColors::class.java.getMethod("getColorHints")
            val isAccessible = m.isAccessible
            if (!isAccessible) m.isAccessible = true
            darkHints = m.invoke(wallpaperColors) as Int
            if (!isAccessible) m.isAccessible = false
        } catch (e: Throwable) {
            val primaryColor = wallpaperColors.primaryColor.toArgb()
            val secondaryColor = wallpaperColors.secondaryColor?.toArgb() ?: primaryColor
            val tertiaryColor = wallpaperColors.tertiaryColor?.toArgb() ?: secondaryColor
            val bitmap =
                generateBitmapFromColors(primaryColor, secondaryColor, tertiaryColor)
            darkHints = calculateDarkHints(bitmap)
        }
        val useDarkTheme = darkHints and HINT_SUPPORTS_DARK_THEME != 0
        return if (useDarkTheme)
            DarkThemeCheckResult.DARK
        else
            DarkThemeCheckResult.LIGHT
    } else { //Android 10+
        return DarkThemeCheckResult.UNDEFINED
    }
}

private fun generateBitmapFromColors(
    @ColorInt primaryColor: Int,
    @ColorInt secondaryColor: Int,
    @ColorInt tertiaryColor: Int
): Bitmap {
    val colors = intArrayOf(primaryColor, secondaryColor, tertiaryColor)
    val imageSize = 6
    val bitmap = Bitmap.createBitmap(imageSize, 1, Bitmap.Config.ARGB_4444)
    for (i in 0 until imageSize / 2)
        bitmap.setPixel(i, 0, colors[0])
    for (i in imageSize / 2 until imageSize / 2 + imageSize / 3)
        bitmap.setPixel(i, 0, colors[1])
    for (i in imageSize / 2 + imageSize / 3 until imageSize)
        bitmap.setPixel(i, 0, colors[2])
    return bitmap
}

private fun calculateDarkHints(source: Bitmap?): Int {
    if (source == null) {
        return 0
    }
    val pixels = IntArray(source.width * source.height)
    var totalLuminance = 0.0
    val maxDarkPixels = (pixels.size * MAX_DARK_AREA).roundToInt()
    var darkPixels = 0
    source.getPixels(
        pixels, 0 /* offset */, source.width, 0 /* x */, 0 /* y */,
        source.width, source.height
    )
    // This bitmap was already resized to fit the maximum allowed area.
// Let's just loop through the pixels, no sweat!
    val tmpHsl = FloatArray(3)
    for (i in pixels.indices) {
        ColorUtils.colorToHSL(pixels[i], tmpHsl)
        val luminance = tmpHsl[2]
        val alpha: Int = Color.alpha(pixels[i])
        // Make sure we don't have a dark pixel mass that will
// make text illegible.
        if (luminance < DARK_PIXEL_LUMINANCE && alpha != 0) {
            darkPixels++
        }
        totalLuminance += luminance.toDouble()
    }
    var hints = 0
    val meanLuminance = totalLuminance / pixels.size
    if (meanLuminance > BRIGHT_IMAGE_MEAN_LUMINANCE && darkPixels < maxDarkPixels) {
        hints = hints or HINT_SUPPORTS_DARK_TEXT
    }
    if (meanLuminance < DARK_THEME_MEAN_LUMINANCE) {
        hints = hints or HINT_SUPPORTS_DARK_THEME
    }
    return hints
}