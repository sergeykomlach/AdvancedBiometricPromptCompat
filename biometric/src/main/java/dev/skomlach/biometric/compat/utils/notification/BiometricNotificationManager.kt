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

package dev.skomlach.biometric.compat.utils.notification

import android.annotation.SuppressLint
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
import dev.skomlach.common.R
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.translate.LocalizationHelper
import java.util.concurrent.atomic.AtomicReference


object BiometricNotificationManager {

    const val CHANNEL_ID = "biometric"
    private val notificationReference = AtomicReference<Runnable>(null)

    private val notificationCompat: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(appContext)
    }


    fun initNotificationsPreferences() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                var notificationChannel = notificationCompat.getNotificationChannel(CHANNEL_ID)
                if (notificationChannel == null) {
                    notificationChannel = NotificationChannel(
                        CHANNEL_ID,
                        LocalizationHelper.getLocalizedString(
                            appContext,
                            R.string.biometriccompat_channel_id
                        ),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    notificationChannel.setShowBadge(false)
                    notificationCompat.createNotificationChannel(notificationChannel)
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun showNotification(
        builder: BiometricPromptCompat.Builder
    ) {
        BiometricLoggerImpl.d("BiometricNotificationManager", "showNotification")
        initNotificationsPreferences()
        dismissAll()
        val notify = Runnable {
            try {
                val clickIntent = Intent()
                for (type in builder.getAllAvailableTypes()) {

                    val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
                        .setOnlyAlertOnce(true)
//                        .setAutoCancel(false)
//                        .setOngoing(true)
                        .setAutoCancel(true)
                        .setLocalOnly(true)
                        .setContentTitle(builder.getTitle())
                        .setContentText(builder.getDescription())
                        .setDeleteIntent(
                            PendingIntent.getBroadcast(
                                appContext,
                                2,
                                clickIntent,
                                //Targeting U+ (version 34 and above) disallows creating or retrieving
                                // a PendingIntent with FLAG_MUTABLE, an implicit Intent within and
                                // without FLAG_NO_CREATE and FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT for
                                // security reasons. To retrieve an already existing PendingIntent,
                                // use FLAG_NO_CREATE, however, to create a new PendingIntent with an implicit Intent use FLAG_IMMUTABLE.
                                if (Utils.isAtLeastS) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_CANCEL_CURRENT
                            )
                        )
                        .setSmallIcon(type.iconId).build()

                    if (
                        PermissionUtils.INSTANCE.isAllowedNotificationsPermission &&
                        PermissionUtils.INSTANCE.isAllowedNotificationsChannelPermission(CHANNEL_ID)
                    ) {
                        notificationCompat.notify(type.hashCode(), notif)
                        BiometricLoggerImpl.d("BiometricNotificationManager", "Notification posted")
                    } else
                        BiometricLoggerImpl.d(
                            "BiometricNotificationManager",
                            "Notifications not allowed"
                        )
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }


        ExecutorHelper.post(notify)

        if (builder.getMultiWindowSupport().isInMultiWindow) {
            notificationReference.set(notify)
            val delay =
                appContext.resources.getInteger(android.R.integer.config_longAnimTime).toLong()
            ExecutorHelper.postDelayed(notify, delay)
        }
    }

    fun dismissAll() {
        notificationReference.get()?.let {
            ExecutorHelper.removeCallbacks(it)
            notificationReference.set(null)
        }

        try {
            for (type in BiometricType.entries) {
                try {
                    notificationCompat.cancel(type.hashCode())
                } catch (e: Throwable) {
                    BiometricLoggerImpl.e(e)
                }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    fun dismiss(type: BiometricType?) {
        try {
            notificationCompat.cancel(type?.hashCode() ?: return)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }
}