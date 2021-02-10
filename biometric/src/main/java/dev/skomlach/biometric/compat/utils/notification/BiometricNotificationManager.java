package dev.skomlach.biometric.compat.utils.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.Set;
import dev.skomlach.biometric.compat.BiometricType;
import dev.skomlach.biometric.compat.R;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.contextprovider.AndroidContext;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BiometricNotificationManager {
    public static BiometricNotificationManager INSTANCE = new BiometricNotificationManager();
    public static final String CHANNEL_ID = "biometric";
    private final NotificationManagerCompat notificationManagerCompat;
    private final Context context;

    private BiometricNotificationManager() {
        context = AndroidContext.getAppContext();
        notificationManagerCompat = NotificationManagerCompat.from(context);
        initNotificationsPreferences();
    }

    private void initNotificationsPreferences() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
                NotificationChannel notificationChannel1 = notificationManager.getNotificationChannel(CHANNEL_ID);
                if (notificationChannel1 == null) {
                    notificationChannel1 = new NotificationChannel(CHANNEL_ID, "Biometric", NotificationManager.IMPORTANCE_LOW);
                }
                notificationChannel1.setShowBadge(false);
                notificationManager.createNotificationChannel(notificationChannel1);
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e);
            }
        }
    }

    public void showNotification(@Nullable CharSequence title, @Nullable CharSequence description, Set<BiometricType> list) {
        dismiss(list);
        try {
            Intent clickIntent = new Intent();
            for (BiometricType type : list) {
                @DrawableRes int icon;
                switch (type) {
                    case BIOMETRIC_FACE:
                        icon = R.drawable.bio_ic_face;
                        break;
                    case BIOMETRIC_IRIS:
                        icon = R.drawable.bio_ic_iris;
                        break;
                    case BIOMETRIC_HEARTRATE:
                        icon = R.drawable.bio_ic_heartrate;
                        break;
                    case BIOMETRIC_VOICE:
                        icon = R.drawable.bio_ic_voice;
                        break;
                    case BIOMETRIC_PALMPRINT:
                        icon = R.drawable.bio_ic_palm;
                        break;
                    case BIOMETRIC_BEHAVIOR:
                        icon = R.drawable.bio_ic_behavior;
                        break;
                    case BIOMETRIC_FINGERPRINT:
                    default:
                        icon = R.drawable.bio_ic_fingerprint;
                        break;
                }
                NotificationCompat.Builder notif = new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setContentTitle(title)
                        .setContentText(description)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(description))
                        .setContentIntent(PendingIntent.getBroadcast(context, 1, clickIntent, PendingIntent.FLAG_CANCEL_CURRENT))
                        .setDeleteIntent(PendingIntent.getBroadcast(context, 2, clickIntent, PendingIntent.FLAG_CANCEL_CURRENT))
                        .setSmallIcon(icon);

                notificationManagerCompat.notify(type.hashCode(), notif.build());
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
    }

    public void dismiss(Set<BiometricType> list) {
        try {
            for (BiometricType type : list) {
                notificationManagerCompat.cancel(type.hashCode());
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
    }
}
