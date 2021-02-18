package dev.skomlach.biometric.compat.utils.themes

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.utils.SettingsHelper

@RestrictTo(RestrictTo.Scope.LIBRARY)
object DarkLightThemes {
    @JvmStatic
    fun isNightMode(context: Context): Boolean {
        return UiModeManager.MODE_NIGHT_YES == getNightMode(context)
    }

    @JvmStatic
    fun getNightMode(context: Context): Int {
        return when (getIsOsDarkTheme(context)) {
            DarkThemeCheckResult.DARK -> {
                UiModeManager.MODE_NIGHT_YES
            }
            DarkThemeCheckResult.LIGHT -> {
                UiModeManager.MODE_NIGHT_NO
            }
            else -> {
                if (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                ) {
                    return UiModeManager.MODE_NIGHT_YES
                }
                if (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                    return UiModeManager.MODE_NIGHT_YES
                }
                val modeFromSettings =
                    SettingsHelper.getInt(context, "ui_night_mode", UiModeManager.MODE_NIGHT_NO)
                if (modeFromSettings != UiModeManager.MODE_NIGHT_NO) {
                    return modeFromSettings
                }
                val mUiModeManager =
                    context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
                mUiModeManager?.currentModeType ?: UiModeManager.MODE_NIGHT_NO
            }
        }
    }
}