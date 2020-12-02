package dev.skomlach.biometric.compat.utils.themes;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.utils.SettingsHelper;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class DarkLightThemes {

    public static boolean isNightMode(Context context) {
        return UiModeManager.MODE_NIGHT_YES == getNightMode(context);
    }

    public static int getNightMode(Context context) {
        DarkThemeCheckResult darkThemeCheckResult = DarkLightThemeDetectionKt.getIsOsDarkTheme(context);
        if (darkThemeCheckResult == DarkThemeCheckResult.DARK) {
            return UiModeManager.MODE_NIGHT_YES;
        } else if (darkThemeCheckResult == DarkThemeCheckResult.LIGHT) {
            return UiModeManager.MODE_NIGHT_NO;
        } else {
            if ((context.getResources().getConfiguration().uiMode &
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return UiModeManager.MODE_NIGHT_YES;
            }

            if ((Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                return UiModeManager.MODE_NIGHT_YES;
            }

            int modeFromSettings = SettingsHelper.getInt(context, "ui_night_mode", UiModeManager.MODE_NIGHT_NO);

            if (modeFromSettings != UiModeManager.MODE_NIGHT_NO) {
                return modeFromSettings;
            }

            UiModeManager mUiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
            return mUiModeManager != null ? mUiModeManager.getCurrentModeType() : UiModeManager.MODE_NIGHT_NO;
        }
    }
}
