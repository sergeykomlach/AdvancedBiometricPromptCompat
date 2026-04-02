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

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import androidx.annotation.StyleRes
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object SystemMonetDialogs {

    private fun isMonetAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @StyleRes
    private fun legacyAlertTheme(base: Context): Int {
        return if (getIsOsDarkTheme(base) == DarkThemeCheckResult.DARK) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
    }

    @StyleRes
    private fun legacyDialogTheme(base: Context): Int {
        return if (getIsOsDarkTheme(base) == DarkThemeCheckResult.DARK) {
            android.R.style.Theme_DeviceDefault_Dialog
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog
        }
    }

    /**
     * Context for dialogs:
     * - Android 12+ -> wrapped with dynamic colors
     * - older Android -> wrapped with DeviceDefault dialog theme
     */
    fun dialogContext(base: Context, alert: Boolean = true): Context {
        return if (isMonetAvailable()) {
            DynamicColors.wrapContextIfAvailable(
                ContextThemeWrapper(
                    base,
                    com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
                ), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
            )
        } else {
            ContextThemeWrapper(
                base,
                if (alert) legacyAlertTheme(base) else legacyDialogTheme(base)
            )
        }
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
        context: Context,
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
        val ctx = dialogContext(context, alert = true)

        return if (isMonetAvailable()) {
            MaterialAlertDialogBuilder(ctx).apply {
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
        } else {
            android.app.AlertDialog.Builder(ctx).apply {
                if (title != null) setTitle(title)
                if (message != null) setMessage(message)
                if (view != null) setView(view)

                if (positiveText != null) {
                    setPositiveButton(positiveText) { dialog, _ -> onPositive?.invoke(dialog) }
                }
                if (negativeText != null) {
                    setNegativeButton(negativeText) { dialog, _ -> onNegative?.invoke(dialog) }
                }
                if (neutralText != null) {
                    setNeutralButton(neutralText) { dialog, _ -> onNeutral?.invoke(dialog) }
                }
            }.create().apply {
                setCancelable(cancelable)
                setOnCancelListener { onCancel?.invoke() }
                setOnDismissListener { onDismiss?.invoke() }
                show()
            }
        }
    }

    /**
     * Returns a plain Dialog (not AlertDialog shell).
     *
     * Android 12+:
     *   Dialog on Monet-wrapped context
     * Older:
     *   Dialog on DeviceDefault dialog theme
     */
    fun createDialog(
        context: Context,
        contentView: View? = null,
        cancelable: Boolean = true,
        alertLike: Boolean = false,
        onCancel: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
    ): Dialog {
        val ctx = if (isMonetAvailable()) {
            DynamicColors.wrapContextIfAvailable(context)
        } else {
            ContextThemeWrapper(
                context,
                if (alertLike) legacyAlertTheme(context) else legacyDialogTheme(context)
            )
        }

        return Dialog(ctx).apply {
            setCancelable(cancelable)
            if (contentView != null) setContentView(contentView)
            setOnCancelListener { onCancel?.invoke() }
            setOnDismissListener { onDismiss?.invoke() }
        }
    }
}