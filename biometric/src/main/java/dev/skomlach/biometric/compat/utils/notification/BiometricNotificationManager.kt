package dev.skomlach.biometric.compat.utils.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RestrictTo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.atomic.AtomicReference

@RestrictTo(RestrictTo.Scope.LIBRARY)
class BiometricNotificationManager private constructor() {

    companion object {
        val INSTANCE = BiometricNotificationManager()
        const val CHANNEL_ID = "biometric"
    }

    private val notificationReference = AtomicReference<Runnable>(null)
    private val notificationManagerCompat: NotificationManagerCompat =
        NotificationManagerCompat.from(appContext)

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
        builder: BiometricPromptCompat.Builder
    ) {
        dismissAll()
        val notify = Runnable {
            try {
                val clickIntent = Intent()
                for (type in builder.allAvailableTypes) {
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
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setLocalOnly(true)
                        .setContentTitle(builder.title)
                        .setContentText(builder.description)
                        .setStyle(
                            NotificationCompat.BigTextStyle()
                                .bigText(builder.description)
                        )
                        .setContentIntent(
                            PendingIntent.getBroadcast(
                                appContext,
                                1,
                                clickIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        )
                        .setDeleteIntent(
                            PendingIntent.getBroadcast(
                                appContext,
                                2,
                                clickIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        )
                        .setSmallIcon(icon).build()

                    notificationManagerCompat.notify(type.hashCode(), notif)
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }

        notificationReference.set(notify)
        ExecutorHelper.INSTANCE.handler.post(notify)

        //update notification to fix icon tinting in split-screen mode
        val delay = appContext.resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        ExecutorHelper.INSTANCE.handler.postDelayed(notify, delay)
    }

    fun dismissAll() {
        notificationReference.get()?.let {
            ExecutorHelper.INSTANCE.handler.removeCallbacks(it)
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
            notificationManagerCompat.cancel(type?.hashCode() ?: return)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }
}