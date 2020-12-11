package dev.skomlach.common.misc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;

import java.util.Iterator;

import dev.skomlach.common.logging.LogCat;

public class BroadcastTools {
    private final static String androidIntentAction = "android.";

    public static void sendGlobalBroadcastIntent(Context context, Intent intent) {

        String action = intent.getAction();
        if (!TextUtils.isEmpty(action) && !action.startsWith(androidIntentAction)) {
            LogCat.logError("BroadcastTools: You tried to send custom global BroadcastIntent. Make sure that action `" + action + "` contains package-specific name");
        }

        context.sendBroadcast(intent);
    }

    public static void registerGlobalBroadcastIntent(Context context, BroadcastReceiver broadcastReceiver, IntentFilter filter) {
        Iterator<String> actionsIterator = filter.actionsIterator();
        while (actionsIterator.hasNext()) {
            String action = actionsIterator.next();
            if (!TextUtils.isEmpty(action) && !action.startsWith(androidIntentAction)) {
                LogCat.logError("BroadcastTools: You tried to register custom global BroadcastReceiver. Make sure that action `" + action + "` contains package-specific name");
            }
        }

        context.registerReceiver(broadcastReceiver, filter);
    }

    public static void unregisterGlobalBroadcastIntent(Context context, BroadcastReceiver broadcastReceiver) {
        context.unregisterReceiver(broadcastReceiver);
    }
}
