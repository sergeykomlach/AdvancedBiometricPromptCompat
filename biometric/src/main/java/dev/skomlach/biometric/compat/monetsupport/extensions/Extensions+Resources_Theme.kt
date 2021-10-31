package dev.skomlach.biometric.compat.monetsupport.extensions

import android.content.res.Resources
import android.graphics.Color
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.RestrictTo
import androidx.core.content.res.use

/**
 *  Gets an attribute color for a given Resources.Theme theme
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@ColorRes
internal fun Resources.Theme.getAttributeColor(
    @AttrRes attribute: Int,
    @ColorInt defColor: Int = Color.TRANSPARENT
): Int? {
    return obtainStyledAttributes(
        intArrayOf(attribute)
    ).use {
        it.getResourceId(0, defColor)
    }
}