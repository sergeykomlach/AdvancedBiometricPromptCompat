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

package dev.skomlach.biometric.compat.utils.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import java.util.concurrent.atomic.AtomicReference


object BiometricNotificationManager {
    const val CHANNEL_ID = "biometric"
    private val notificationReference = AtomicReference<Runnable>(null)

    init {
        initNotificationsPreferences()
    }

    private fun initNotificationsPreferences() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val notificationManager: NotificationManager? = appContext.getSystemService(
                    NotificationManager::class.java
                )
                var notificationChannel1 = notificationManager?.getNotificationChannel(CHANNEL_ID)
                if (notificationChannel1 == null) {
                    notificationChannel1 = NotificationChannel(
                        CHANNEL_ID,
                        "Biometric",
                        NotificationManager.IMPORTANCE_LOW
                    )
                }
                notificationChannel1.setShowBadge(false)
                notificationManager?.createNotificationChannel(notificationChannel1)
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
    }

    fun showNotification(
        builder: BiometricPromptCompat.Builder
    ) {
        dismissAll()
        val notify = Runnable {
            try {
                val clickIntent = Intent()
                for (type in builder.getAllAvailableTypes()) {

                    val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
                        .setOnlyAlertOnce(true)
                        //TODO: investigate why notifications remains on the screen
//                        .setAutoCancel(false)
//                        .setOngoing(true)

                        .setAutoCancel(true)
                        .setLocalOnly(true)
                        .setContentTitle(builder.getTitle())
                        .setContentText(builder.getDescription())
                        .setStyle(
                            NotificationCompat.BigTextStyle()
                                .bigText(builder.getDescription())
                        )
                        .setDeleteIntent(
                            PendingIntent.getBroadcast(
                                appContext,
                                2,
                                clickIntent,
                                if (Utils.isAtLeastS) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        )
                        .setSmallIcon(type.iconId).build()

                    NotificationManagerCompat.from(appContext).notify(type.hashCode(), notif)
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }

        notificationReference.set(notify)
        ExecutorHelper.post(notify)

        //update notification to fix icon tinting in split-screen mode
        val delay = appContext.resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        ExecutorHelper.postDelayed(notify, delay)
    }

    fun dismissAll() {
        notificationReference.get()?.let {
            ExecutorHelper.removeCallbacks(it)
            notificationReference.set(null)
        }
        try {
            for (type in BiometricType.values()) {
                dismiss(type)
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    fun dismiss(type: BiometricType?) {
        try {
            NotificationManagerCompat.from(appContext).cancel(type?.hashCode() ?: return)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }
}