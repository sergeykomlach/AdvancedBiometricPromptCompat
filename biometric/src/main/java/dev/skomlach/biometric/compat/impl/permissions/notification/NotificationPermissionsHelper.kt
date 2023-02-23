package dev.skomlach.biometric.compat.impl.permissions.notification

import android.content.Context
import dev.skomlach.common.permissions.PermissionUtils

object NotificationPermissionsHelper {
    fun checkNotificationPermissions(
        context: Context, channelId: String, runnableOk: Runnable, runnableFailed: Runnable? = null
    ) {
        if (!PermissionUtils.isAllowedNotificationsPermission) {
            PermissionRequestController.askNotificationsPermission(context, {
                checkNotificationChannelPermissions(
                    context, channelId, runnableOk, runnableFailed
                )
            }) {
                runnableFailed?.let {
                    it.run()
                }

            }
        } else {
            checkNotificationChannelPermissions(context, channelId, runnableOk, runnableFailed)
        }
    }

    private fun checkNotificationChannelPermissions(
        context: Context, channelId: String, runnableOk: Runnable, runnableFailed: Runnable?
    ) {
        if (!PermissionUtils.isAllowedNotificationsChannelPermission(channelId)) {
            PermissionRequestController.askNotificationsChannelsPermission(context,
                channelId,
                { runnableOk.run() }) {
                runnableFailed?.let {
                    it.run()
                }
            }
        } else {
            runnableOk.run()
        }
    }

}