package dev.skomlach.biometric.compat.utils.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RestrictTo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext.appContext

@RestrictTo(RestrictTo.Scope.LIBRARY)
class BiometricNotificationManager private constructor() {

    companion object {
        val INSTANCE = BiometricNotificationManager()
        const val CHANNEL_ID = "biometric"
    }

    private val notificationManagerCompat: NotificationManagerCompat = NotificationManagerCompat.from(appContext)

    init {
        initNotificationsPreferences()
    }

    private fun initNotificationsPreferences() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val notificationManager = appContext.getSystemService(
                    NotificationManager::class.java
                )
                var notificationChannel1 = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (notificationChannel1 == null) {
                    notificationChannel1 = NotificationChannel(
                        CHANNEL_ID,
                        "Biometric",
                        NotificationManager.IMPORTANCE_LOW
                    )
                }
                notificationChannel1.setShowBadge(false)
                notificationManager.createNotificationChannel(notificationChannel1)
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
    }

    fun showNotification(
        title: CharSequence?,
        description: CharSequence?,
        list: Set<BiometricType>
    ) {
        dismissAll()
        try {
            val clickIntent = Intent()
            for (type in list) {
                @DrawableRes val icon: Int = when (type) {
                    BiometricType.BIOMETRIC_FACE -> R.drawable.bio_ic_face
                    BiometricType.BIOMETRIC_IRIS -> R.drawable.bio_ic_iris
                    BiometricType.BIOMETRIC_HEARTRATE -> R.drawable.bio_ic_heartrate
                    BiometricType.BIOMETRIC_VOICE -> R.drawable.bio_ic_voice
                    BiometricType.BIOMETRIC_PALMPRINT -> R.drawable.bio_ic_palm
                    BiometricType.BIOMETRIC_BEHAVIOR -> R.drawable.bio_ic_behavior
                    BiometricType.BIOMETRIC_FINGERPRINT -> R.drawable.bio_ic_fingerprint
                    else -> R.drawable.bio_ic_fingerprint
                }
                val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setLocalOnly(true)
                    .setContentTitle(title)
                    .setContentText(description)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(description)
                    )
                    .setContentIntent(
                        PendingIntent.getBroadcast(
                            appContext,
                            1,
                            clickIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT
                        )
                    )
                    .setDeleteIntent(
                        PendingIntent.getBroadcast(
                            appContext,
                            2,
                            clickIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT
                        )
                    )
                    .setSmallIcon(icon)
                notificationManagerCompat.notify(type.hashCode(), notif.build())
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    fun dismissAll() {
        try {
            for (type in BiometricType.values()) {
                notificationManagerCompat.cancel(type.hashCode())
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }
}