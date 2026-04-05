/*
 *  Copyright (c) 2026 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

import android.app.Activity
import android.app.Dialog
import android.app.UiModeManager
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.skomlach.common.R
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.SettingsHelper
import dev.skomlach.common.misc.Utils
import java.time.LocalTime

object SystemMonetDialogs {

    private fun dialogContext(base: Context, res: Int): Context {
        val themed = ContextThemeWrapper(base, res)
        return DynamicColors.wrapContextIfAvailable(themed)
    }

    /**
     * Returns a shown AlertDialog.
     *
     * Android 12+:
     *   Material dialog + Monet(dynamic colors)
     * Older:
     *   framework AlertDialog with DeviceDefault dialog-alert theme
     */
    fun showAlertDialog(
        context: Activity,
        title: CharSequence? = null,
        message: CharSequence? = null,
        positiveText: CharSequence? = null,
        negativeText: CharSequence? = null,
        neutralText: CharSequence? = null,
        cancelable: Boolean = true,
        view: View? = null,
        onPositive: ((DialogInterface) -> Unit)? = null,
        onNegative: ((DialogInterface) -> Unit)? = null,
        onNeutral: ((DialogInterface) -> Unit)? = null,
        onCancel: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
    ): Dialog {
        try {
            val darkTheme = DarkLightThemes.isNightMode(context)
            val targetThemeRes = when (darkTheme) {
                true -> R.style.BiometricTheme_SystemDialog_Dark
                else -> R.style.BiometricTheme_SystemDialog_Light
            }


            LogCat.log("SystemMonetDialogs", "darkTheme=$darkTheme; themeId=$targetThemeRes")
            val ctx = dialogContext(context, targetThemeRes)
            return MaterialAlertDialogBuilder(ctx).apply {
                if (title != null) setTitle(title)
                if (message != null) setMessage(message)
                if (view != null) setView(view)
                setCancelable(cancelable)

                if (positiveText != null) {
                    setPositiveButton(positiveText) { dialog, _ -> onPositive?.invoke(dialog) }
                }
                if (negativeText != null) {
                    setNegativeButton(negativeText) { dialog, _ -> onNegative?.invoke(dialog) }
                }
                if (neutralText != null) {
                    setNeutralButton(neutralText) { dialog, _ -> onNeutral?.invoke(dialog) }
                }

                setOnCancelListener { onCancel?.invoke() }
                setOnDismissListener { onDismiss?.invoke() }
            }.show()
        } catch (e: Exception) {
            LogCat.logError("SystemMonetDialogs", e)
            throw e
        }
    }

    internal object DarkLightThemes {

        fun isNightMode(context: Context): Boolean {
            return UiModeManager.MODE_NIGHT_YES == getNightMode(context)
        }


        private fun getNightMode(context: Context): Int {
            return when (getIsOsDarkTheme(context)) {
                DarkThemeCheckResult.DARK -> {
                    UiModeManager.MODE_NIGHT_YES
                }

                DarkThemeCheckResult.LIGHT -> {
                    UiModeManager.MODE_NIGHT_NO
                }

                else -> {
                    Resources.getSystem().configuration?.let { config ->
                        if ((config.uiMode and
                                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) ||
                            (Utils.isAtLeastR && config.isNightModeActive)
                        )
                            return UiModeManager.MODE_NIGHT_YES
                    }

                    val mUiModeManager =
                        context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
                    val modeFromSettings =
                        SettingsHelper.getInt(context, "ui_night_mode", UiModeManager.MODE_NIGHT_NO)
                    if (modeFromSettings != UiModeManager.MODE_NIGHT_NO) {
                        if (modeFromSettings == UiModeManager.MODE_NIGHT_YES)
                            return UiModeManager.MODE_NIGHT_YES
                        else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val start = mUiModeManager?.customNightModeStart
                                val end = mUiModeManager?.customNightModeEnd
                                val now = LocalTime.now()
                                if (now.isAfter(start) && now.isBefore(end))
                                    return UiModeManager.MODE_NIGHT_YES
                            }
                        }
                    } else {
                        val nightMode = mUiModeManager?.nightMode ?: UiModeManager.MODE_NIGHT_NO
                        if (nightMode != UiModeManager.MODE_NIGHT_NO) {
                            if (nightMode == UiModeManager.MODE_NIGHT_YES)
                                return UiModeManager.MODE_NIGHT_YES
                            else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val start = mUiModeManager?.customNightModeStart
                                    val end = mUiModeManager?.customNightModeEnd
                                    val now = LocalTime.now()
                                    if ((now.equals(start) || now.equals(end)) || (now.isAfter(start) && now.isBefore(
                                            end
                                        ))
                                    )
                                        return UiModeManager.MODE_NIGHT_YES
                                }
                            }
                        }
                    }
                    UiModeManager.MODE_NIGHT_NO
                }
            }
        }
    }
}