package dev.skomlach.biometric.compat.monetsupport.extensions

import android.content.Context
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes

/**
 *  Returns whether the current context has a dark configuration
 */
internal val Context.isDarkMode: Boolean
    get() {
        return DarkLightThemes.isNightMode(this)
    }