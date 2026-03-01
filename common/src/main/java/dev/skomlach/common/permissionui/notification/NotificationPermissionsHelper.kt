package dev.skomlach.common.permissionui.notification

import android.app.Activity
import dev.skomlach.common.permissions.PermissionUtils

object NotificationPermissionsHelper {
    fun checkNotificationPermissions(
        context: Activity?,
        channelId: String,
        runnableOk: Runnable,
        runnableFailed: Runnable? = null
    ) {
        if (!PermissionUtils.INSTANCE.isAllowedNotificationsPermission) {
            PermissionRequestController.askNotificationsPermission(context, {
                checkNotificationChannelPermissions(
                    context, channelId, runnableOk, runnableFailed
                )
            }) {
                runnableFailed?.run()
            }
        } else {
            checkNotificationChannelPermissions(context, channelId, runnableOk, runnableFailed)
        }
    }

    private fun checkNotificationChannelPermissions(
        context: Activity?, channelId: String, runnableOk: Runnable, runnableFailed: Runnable?
    ) {
        if (!PermissionUtils.INSTANCE.isAllowedNotificationsChannelPermission(channelId)) {
            PermissionRequestController.askNotificationsChannelsPermission(
                context,
                channelId,
                { runnableOk.run() }) {
                runnableFailed?.run()
            }
        } else {
            runnableOk.run()
        }
    }

}