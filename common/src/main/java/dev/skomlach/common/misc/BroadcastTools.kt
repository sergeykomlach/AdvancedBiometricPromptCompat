package dev.skomlach.common.misc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.TextUtils
import dev.skomlach.common.logging.LogCat.logError

object BroadcastTools {
    private const val androidIntentAction = "android."

    @JvmStatic
    fun sendGlobalBroadcastIntent(context: Context, intent: Intent) {
        val action = intent.action
        if (!TextUtils.isEmpty(action) && action?.startsWith(androidIntentAction) == false) {
            logError("BroadcastTools: You tried to send custom global BroadcastIntent. Make sure that action `$action` contains package-specific name")
        }
        context.sendBroadcast(intent)
    }

    @JvmStatic
    fun registerGlobalBroadcastIntent(
        context: Context,
        broadcastReceiver: BroadcastReceiver?,
        filter: IntentFilter
    ) {
        val actionsIterator = filter.actionsIterator()
        while (actionsIterator.hasNext()) {
            val action = actionsIterator.next()
            if (!TextUtils.isEmpty(action) && !action.startsWith(androidIntentAction)) {
                logError("BroadcastTools: You tried to register custom global BroadcastReceiver. Make sure that action `$action` contains package-specific name")
            }
        }
        context.registerReceiver(broadcastReceiver, filter)
    }

    @JvmStatic
    fun unregisterGlobalBroadcastIntent(context: Context, broadcastReceiver: BroadcastReceiver?) {
        context.unregisterReceiver(broadcastReceiver)
    }
}