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

package dev.skomlach.common.statusbar

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.view.Window
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.toBitmap
import dev.skomlach.common.logging.LogCat

internal data class TaskDescriptionColors(
    @param:ColorInt val primaryColor: Int?,
    @param:ColorInt val backgroundColor: Int?,
    @param:ColorInt val statusBarColor: Int,
    @param:ColorInt val navigationBarColor: Int
)

internal fun resolveTaskDescriptionColors(
    @ColorInt statusBarColor: Int,
    @ColorInt navigationBarColor: Int
): TaskDescriptionColors {
    val opaqueTaskColor = statusBarColor.takeIf { it ushr 24 == 0xFF }
    return TaskDescriptionColors(
        primaryColor = opaqueTaskColor,
        backgroundColor = opaqueTaskColor,
        statusBarColor = statusBarColor,
        navigationBarColor = navigationBarColor
    )
}

@Suppress("DEPRECATION")
internal fun updateTaskDescriptionCompat(
    window: Window,
    @ColorInt statusBarColor: Int,
    @ColorInt navigationBarColor: Int
) {
    val activity = window.callback as? Activity ?: return
    val colors = resolveTaskDescriptionColors(statusBarColor, navigationBarColor)
    val iconRes = activity.applicationInfo.icon

    try {
        val taskDescription = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ActivityManager.TaskDescription.Builder().apply {
                    colors.primaryColor?.let { setPrimaryColor(it) }
                    colors.backgroundColor?.let { setBackgroundColor(it) }
                    setStatusBarColor(colors.statusBarColor)
                    setNavigationBarColor(colors.navigationBarColor)
                }.build()
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                if (iconRes != 0) {
                    ActivityManager.TaskDescription(null, iconRes, colors.primaryColor ?: 0)
                } else {
                    ActivityManager.TaskDescription(
                        null,
                        activity.applicationInfo.loadIcon(activity.packageManager).toBitmap(),
                        colors.primaryColor ?: 0
                    )
                }
            }

            else -> ActivityManager.TaskDescription(
                null,
                activity.applicationInfo.loadIcon(activity.packageManager).toBitmap(),
                colors.primaryColor ?: 0
            )
        }
        activity.setTaskDescription(taskDescription)
    } catch (e: Throwable) {
        LogCat.logException(e)
    }
}
